// Pion WebRTC IoT Mock Server
//
// Simulates an IoT device with WHIP/WHEP endpoints and DataChannel support.
// Used for Level 3 manual testing of SyncAI-Lib-KmpWebRTC.
//
// Endpoints:
//   POST /{stream}/whip  - Publish stream (WHIP)
//   POST /{stream}/whep  - Subscribe to stream (WHEP)
//   PATCH /resource/{id}  - Trickle ICE
//   DELETE /resource/{id} - Teardown
//   GET /health           - Health check
//
// DataChannel:
//   - Echoes text messages back to sender
//   - Responds to JSON commands: {"cmd":"get_status"} -> {"status":"ok","uptime":...}

package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/pion/webrtc/v4"
)

var (
	startTime = time.Now()

	// sessions stores active peer connections
	sessions   = make(map[string]*Session)
	sessionsMu sync.RWMutex

	// streams stores published tracks for SFU forwarding
	streams   = make(map[string]*Stream)
	streamsMu sync.RWMutex
)

// Session represents an active WebRTC session
type Session struct {
	ID             string
	PC             *webrtc.PeerConnection
	Stream         string
	Protocol       string // "whip" or "whep"
	ICECandidates  []webrtc.ICECandidateInit
	GatherComplete chan struct{}
}

// Stream stores published tracks for SFU distribution
type Stream struct {
	VideoTrack *webrtc.TrackLocalStaticRTP
	AudioTrack *webrtc.TrackLocalStaticRTP
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/health", handleHealth)
	mux.HandleFunc("/", handleRoot)

	log.Printf("Pion IoT server starting on :%s", port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatal(err)
	}
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	uptime := int(time.Since(startTime).Seconds())
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprintf(w, `{"status":"ok","uptime":%d}`, uptime)
}

func handleRoot(w http.ResponseWriter, r *http.Request) {
	path := strings.TrimPrefix(r.URL.Path, "/")
	parts := strings.Split(path, "/")

	switch {
	// POST /{stream}/whip or /{stream}/whep
	case r.Method == "POST" && len(parts) == 2 && (parts[1] == "whip" || parts[1] == "whep"):
		handleOffer(w, r, parts[0], parts[1])

	// PATCH /resource/{id}
	case r.Method == "PATCH" && len(parts) == 2 && parts[0] == "resource":
		handleICECandidate(w, r, parts[1])

	// DELETE /resource/{id}
	case r.Method == "DELETE" && len(parts) == 2 && parts[0] == "resource":
		handleDelete(w, r, parts[1])

	default:
		http.NotFound(w, r)
	}
}

func handleOffer(w http.ResponseWriter, r *http.Request, stream, protocol string) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Failed to read body", http.StatusBadRequest)
		return
	}

	offer := webrtc.SessionDescription{
		Type: webrtc.SDPTypeOffer,
		SDP:  string(body),
	}

	// Create PeerConnection
	config := webrtc.Configuration{
		ICEServers: []webrtc.ICEServer{
			{URLs: []string{"stun:stun.l.google.com:19302"}},
		},
	}

	pc, err := webrtc.NewPeerConnection(config)
	if err != nil {
		http.Error(w, "Failed to create PeerConnection: "+err.Error(), http.StatusInternalServerError)
		return
	}

	sessionID := fmt.Sprintf("%s-%s-%d", stream, protocol, time.Now().UnixNano())
	gatherComplete := make(chan struct{})

	session := &Session{
		ID:             sessionID,
		PC:             pc,
		Stream:         stream,
		Protocol:       protocol,
		GatherComplete: gatherComplete,
	}

	sessionsMu.Lock()
	sessions[sessionID] = session
	sessionsMu.Unlock()

	// Handle ICE gathering completion
	pc.OnICEGatheringStateChange(func(state webrtc.ICEGatheringState) {
		if state == webrtc.ICEGatheringStateComplete {
			select {
			case <-gatherComplete:
			default:
				close(gatherComplete)
			}
		}
	})

	pc.OnConnectionStateChange(func(state webrtc.PeerConnectionState) {
		log.Printf("[%s] Connection state: %s", sessionID, state.String())
		if state == webrtc.PeerConnectionStateFailed || state == webrtc.PeerConnectionStateClosed {
			cleanupSession(sessionID)
		}
	})

	if protocol == "whip" {
		// WHIP: remote is publishing to us
		setupWHIP(pc, session, stream)
	} else {
		// WHEP: remote wants to receive from us
		setupWHEP(pc, session, stream)
	}

	// Set remote description (the offer)
	if err = pc.SetRemoteDescription(offer); err != nil {
		http.Error(w, "Failed to set remote description: "+err.Error(), http.StatusBadRequest)
		pc.Close()
		return
	}

	// Create answer
	answer, err := pc.CreateAnswer(nil)
	if err != nil {
		http.Error(w, "Failed to create answer: "+err.Error(), http.StatusInternalServerError)
		pc.Close()
		return
	}

	if err = pc.SetLocalDescription(answer); err != nil {
		http.Error(w, "Failed to set local description: "+err.Error(), http.StatusInternalServerError)
		pc.Close()
		return
	}

	// Wait for ICE gathering to complete (full ICE mode)
	select {
	case <-gatherComplete:
	case <-time.After(10 * time.Second):
		log.Printf("[%s] ICE gathering timeout, sending partial candidates", sessionID)
	}

	// Send answer with gathered candidates
	sdpAnswer := pc.LocalDescription().SDP

	w.Header().Set("Content-Type", "application/sdp")
	w.Header().Set("Location", "/resource/"+sessionID)
	w.Header().Set("ETag", fmt.Sprintf(`"%s"`, sessionID[:8]))
	w.WriteHeader(http.StatusCreated)
	w.Write([]byte(sdpAnswer))

	log.Printf("[%s] %s session created for stream '%s'", sessionID, strings.ToUpper(protocol), stream)
}

func setupWHIP(pc *webrtc.PeerConnection, session *Session, stream string) {
	// Accept incoming tracks from publisher
	pc.OnTrack(func(track *webrtc.TrackRemote, receiver *webrtc.RTPReceiver) {
		log.Printf("[%s] Received %s track: %s", session.ID, track.Kind().String(), track.Codec().MimeType)

		// Create local track for SFU forwarding
		localTrack, err := webrtc.NewTrackLocalStaticRTP(
			track.Codec().RTPCodecCapability,
			track.Kind().String(),
			stream,
		)
		if err != nil {
			log.Printf("[%s] Failed to create local track: %v", session.ID, err)
			return
		}

		// Store track for SFU distribution
		streamsMu.Lock()
		if streams[stream] == nil {
			streams[stream] = &Stream{}
		}
		if track.Kind() == webrtc.RTPCodecTypeVideo {
			streams[stream].VideoTrack = localTrack
		} else {
			streams[stream].AudioTrack = localTrack
		}
		streamsMu.Unlock()

		// Forward RTP packets to all subscribers.
		// Ignore write errors (a subscriber may have disconnected) so the
		// forwarding loop keeps running for the remaining subscribers.
		buf := make([]byte, 1500)
		for {
			n, _, err := track.Read(buf)
			if err != nil {
				log.Printf("[%s] Track read ended: %v", session.ID, err)
				return
			}
			if _, err = localTrack.Write(buf[:n]); err != nil {
				log.Printf("[%s] Write error (subscriber may have disconnected): %v", session.ID, err)
			}
		}
	})

	// Setup DataChannel handling for incoming channels
	pc.OnDataChannel(func(dc *webrtc.DataChannel) {
		setupDataChannel(dc, session.ID)
	})
}

func setupWHEP(pc *webrtc.PeerConnection, session *Session, stream string) {
	// Check if there's a published stream to forward
	streamsMu.RLock()
	s := streams[stream]
	streamsMu.RUnlock()

	if s != nil {
		// SFU mode: forward existing tracks
		if s.VideoTrack != nil {
			if _, err := pc.AddTrack(s.VideoTrack); err != nil {
				log.Printf("[%s] Failed to add video track: %v", session.ID, err)
			}
		}
		if s.AudioTrack != nil {
			if _, err := pc.AddTrack(s.AudioTrack); err != nil {
				log.Printf("[%s] Failed to add audio track: %v", session.ID, err)
			}
		}
	} else {
		// IoT mode: generate test pattern
		videoTrack, err := webrtc.NewTrackLocalStaticRTP(
			webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeVP8},
			"video",
			stream,
		)
		if err == nil {
			if _, err := pc.AddTrack(videoTrack); err != nil {
				log.Printf("[%s] Failed to add test video track: %v", session.ID, err)
			} else {
				go generateTestPattern(videoTrack, session.ID)
			}
		}

		audioTrack, err := webrtc.NewTrackLocalStaticRTP(
			webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeOpus},
			"audio",
			stream,
		)
		if err == nil {
			if _, err := pc.AddTrack(audioTrack); err != nil {
				log.Printf("[%s] Failed to add test audio track: %v", session.ID, err)
			} else {
				go generateSilence(audioTrack, session.ID)
			}
		}
	}

	// Create DataChannel for IoT commands
	dc, err := pc.CreateDataChannel("iot-control", nil)
	if err == nil {
		setupDataChannel(dc, session.ID)
	}
}

func setupDataChannel(dc *webrtc.DataChannel, sessionID string) {
	dc.OnOpen(func() {
		log.Printf("[%s] DataChannel '%s' opened", sessionID, dc.Label())
	})

	dc.OnMessage(func(msg webrtc.DataChannelMessage) {
		if msg.IsString {
			text := string(msg.Data)
			log.Printf("[%s] DC message: %s", sessionID, text)

			// Try to parse as JSON command
			var cmd map[string]interface{}
			if err := json.Unmarshal(msg.Data, &cmd); err == nil {
				if cmdStr, ok := cmd["cmd"].(string); ok {
					response := handleCommand(cmdStr, cmd)
					respBytes, _ := json.Marshal(response)
					dc.SendText(string(respBytes))
					return
				}
			}

			// Default: echo back
			dc.SendText(text)
		} else {
			// Binary: echo back
			dc.Send(msg.Data)
		}
	})

	dc.OnClose(func() {
		log.Printf("[%s] DataChannel '%s' closed", sessionID, dc.Label())
	})
}

func handleCommand(cmd string, payload map[string]interface{}) map[string]interface{} {
	switch cmd {
	case "get_status":
		return map[string]interface{}{
			"status": "ok",
			"uptime": int(time.Since(startTime).Seconds()),
			"cmd":    "get_status_response",
		}
	case "move":
		direction, _ := payload["direction"].(string)
		return map[string]interface{}{
			"cmd":       "move_response",
			"status":    "ok",
			"direction": direction,
			"message":   fmt.Sprintf("Moving %s", direction),
		}
	default:
		return map[string]interface{}{
			"cmd":     "unknown_command",
			"status":  "error",
			"message": fmt.Sprintf("Unknown command: %s", cmd),
		}
	}
}

func handleICECandidate(w http.ResponseWriter, r *http.Request, id string) {
	sessionsMu.RLock()
	session, ok := sessions[id]
	sessionsMu.RUnlock()

	if !ok {
		http.Error(w, "Session not found", http.StatusNotFound)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Failed to read body", http.StatusBadRequest)
		return
	}

	// Parse trickle ICE candidate from SDP fragment
	candidateStr := string(body)
	lines := strings.Split(candidateStr, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "a=candidate:") {
			candidate := strings.TrimPrefix(line, "a=")
			if err := session.PC.AddICECandidate(webrtc.ICECandidateInit{
				Candidate: candidate,
			}); err != nil {
				log.Printf("[%s] Failed to add ICE candidate: %v", id, err)
			}
		}
	}

	w.WriteHeader(http.StatusNoContent)
}

func handleDelete(w http.ResponseWriter, r *http.Request, id string) {
	cleanupSession(id)
	w.WriteHeader(http.StatusOK)
	log.Printf("[%s] Session deleted", id)
}

func cleanupSession(id string) {
	sessionsMu.Lock()
	session, ok := sessions[id]
	if ok {
		delete(sessions, id)
	}
	sessionsMu.Unlock()

	if ok && session.PC != nil {
		session.PC.Close()
	}
}

// generateTestPattern sends minimal VP8 keyframes as a test video source
func generateTestPattern(track *webrtc.TrackLocalStaticRTP, sessionID string) {
	// Minimal VP8 keyframe (10x10 green frame)
	// VP8 payload descriptor (1 byte) + VP8 payload header (3 bytes for keyframe) + partition
	vp8Keyframe := []byte{
		0x10,                   // VP8 payload descriptor: S=1, PID=0
		0x9d, 0x01, 0x2a,      // VP8 keyframe tag
		0x0a, 0x00,             // width: 10
		0x0a, 0x00,             // height: 10
		0x01, 0x40, 0x25, 0xa4, // minimal partition data
		0x00, 0x03, 0x70, 0x00,
		0xfe, 0xfb, 0x94, 0x00,
		0x00,
	}

	ticker := time.NewTicker(33 * time.Millisecond) // ~30fps
	defer ticker.Stop()

	seq := uint16(0)
	ts := uint32(0)

	for range ticker.C {
		sessionsMu.RLock()
		_, exists := sessions[sessionID]
		sessionsMu.RUnlock()
		if !exists {
			return
		}

		// Build RTP packet manually
		header := make([]byte, 12)
		header[0] = 0x80         // V=2, P=0, X=0, CC=0
		header[1] = 0x60 | 96   // M=0, PT=96 (VP8)
		header[2] = byte(seq >> 8)
		header[3] = byte(seq)
		header[4] = byte(ts >> 24)
		header[5] = byte(ts >> 16)
		header[6] = byte(ts >> 8)
		header[7] = byte(ts)
		// SSRC = 1
		header[8] = 0
		header[9] = 0
		header[10] = 0
		header[11] = 1

		packet := append(header, vp8Keyframe...)
		track.Write(packet)

		seq++
		ts += 3000 // 90000 Hz / 30 fps
	}
}

// generateSilence sends silent Opus frames
func generateSilence(track *webrtc.TrackLocalStaticRTP, sessionID string) {
	// Minimal Opus silence frame (DTX)
	silenceFrame := []byte{0xf8, 0xff, 0xfe}

	ticker := time.NewTicker(20 * time.Millisecond) // 50fps = 20ms Opus frames
	defer ticker.Stop()

	seq := uint16(0)
	ts := uint32(0)

	for range ticker.C {
		sessionsMu.RLock()
		_, exists := sessions[sessionID]
		sessionsMu.RUnlock()
		if !exists {
			return
		}

		header := make([]byte, 12)
		header[0] = 0x80
		header[1] = 0x60 | 111 // PT=111 (Opus)
		header[2] = byte(seq >> 8)
		header[3] = byte(seq)
		header[4] = byte(ts >> 24)
		header[5] = byte(ts >> 16)
		header[6] = byte(ts >> 8)
		header[7] = byte(ts)
		header[8] = 0
		header[9] = 0
		header[10] = 0
		header[11] = 2 // SSRC = 2

		packet := append(header, silenceFrame...)
		track.Write(packet)

		seq++
		ts += 960 // 48000 Hz / 50 fps
	}
}
