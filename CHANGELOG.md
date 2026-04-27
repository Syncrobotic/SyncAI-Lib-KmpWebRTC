# Changelog

## [2.1.0](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/compare/v2.0.1...v2.1.0) (2026-04-27)


### Features

* use custom http engine for different platform ([2f07e0a](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/2f07e0a37d96945c6779201b87f2b76a865d2f5c))
* use custom http engine for different platform ([6909bf5](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/6909bf560ea6174cc9011d1fcde16ebc1f1a6be8))

## [2.0.1](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/compare/v2.0.0...v2.0.1) (2026-04-14)


### Bug Fixes

* avoid displaying Int.MAX_VALUE in reconnecting UI for unlimited retries ([2b17b9e](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/2b17b9e1919110cd058943d562cdafafed9c7c03))
* update AudioPushState and AudioPlaybackState Reconnecting to use nullable maxAttempts ([25a565c](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/25a565cea1c0a53ec6143fa94593e39a44374788))
* use nullable maxAttempts in Reconnecting states to avoid displaying Int.MAX_VALUE ([dfe52b2](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/dfe52b229b62275fc9fba4f3899c171c680c16fc))

## [2.0.0](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/compare/v1.4.1...v2.0.0) (2026-04-08)


### ⚠ BREAKING CHANGES

* The following deprecated classes and functions have been removed:

### Features

* add AudioPlayer composable for receiving remote audio ([8a7734c](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/8a7734c8dd95660116fc8178236f7ee52cae04f0))
* add camera capture support for all platforms (Android/iOS/JVM) ([cc9c866](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/cc9c86672f5e7b79ecd16031c93280964b14909b))
* add CameraPreview composable for local camera display ([b74d2f7](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/b74d2f74796b86feb8d5e8baa2ae650653cd0c4d))
* add Level 3 manual test infrastructure for server/client architecture tests ([bf5bfd8](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/bf5bfd822d6fe63a20d6c31c08e7b1e9f90cde84))
* add setRemoteVideoEnabled for incoming video control ([7609e84](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/7609e849e83a79e0b5ba87c9e2bc691494ed1801))
* add unified WebRTCSession with flexible media direction support ([1e6958d](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/1e6958d0d70c483f909d1ed1cdaf7be0e2c37fe4))
* expose public callbacks for custom video rendering ([b6dbdef](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/b6dbdefe37bbd4bf5ca4ee83a3f8e9f329eef3c4))
* implement E2E test infrastructure and 7 test classes ([af2c356](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/af2c3567e8be5228587ba605f3369b9e746e69dd))
* remove all deprecated APIs — unified WebRTCSession is now the only public API ([6b0bf65](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/6b0bf65f8523171e1ab09b624dce1ab4bffc94cc))


### Bug Fixes

* **android:** remove duplicate addTrack() causing send audio/video failure ([7ac0c37](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/7ac0c372a6969634ac396b32b637813079ca882b))
* **android:** resolve green screen flashing in VideoRenderer ([448a719](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/448a71939e0281fb5673f12f5bf613f22edeff6e))
* disable mediamtx API auth for testing ([dd4e391](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/dd4e391959d0bb9952e41fddabf969a678b49c57))
* level 3 infra didn't work ([255afaf](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/255afaf6965b13c01c550d41a70baeedc05275bd))
* resolve Android green screen and SEND_VIDEO connection failure ([f341345](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/f341345603598442a96c15b7e5ad6342e1d87e6f))
* resolve Testcontainers + Docker Engine 29 compatibility ([1f8677a](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/1f8677a5abf3cd9e44853ba6e9aca3bf29549c69))
* **test-infra:** pion SFU keeps forwarding when a subscriber disconnects ([314dc6c](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/314dc6c09d901ac49f1832a00c432563eb033ee4))
* use WebRTC port for mediamtx healthcheck ([e0d0cd7](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/e0d0cd7461aa3acbe40c21e52e1cefcf02f3da4f))

## [1.4.1](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/compare/v1.4.0...v1.4.1) (2026-04-01)


### Bug Fixes

* resolve integer overflow in StreamRetryHandler when using PERSISTENT retry config and add session logging across all platforms ([146a459](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/146a4597c6eb8fa46cb2e3efdea192117f763af1))
* resolve reconnect failures across all platforms (Android/JVM/iOS) ([fc6b99a](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/fc6b99a9528ec335941d320987431e2ff6c2d9d8))

## [1.4.0](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/compare/v1.3.0...v1.4.0) (2026-03-31)


### Features

* trigger release-please ([c26af4e](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/c26af4eab1381d9535d0bac269d43ed702a2ba2c))
* trigger release-please ([d4b6ed6](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/d4b6ed6d54009ce95a332b37c06355ec3bb8a8f7))

## [1.3.0](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/compare/v1.2.0...v1.3.0) (2026-03-31)


### Features

* add RetryConfig.PERSISTENT and trigger reconnect on ICE FAILED ([02f1f4c](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/02f1f4c21bb2f2dd21d70970f3fae1f32c18c412))

## [1.2.0](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/compare/v1.1.0...v1.2.0) (2026-03-31)


### Features

* add session-based VideoRenderer and AudioPushPlayer, deprecate BidirectionalPlayer and WebRTCClient ([c6b6998](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/c6b6998ceae6ba99b3d3f2e19cb4cdc2e654b447))
* add SessionStatusOverlay for video disconnect indication ([d257661](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/d2576617b161b37868e5de884b5f8f6e9748f696))
* add SignalingAdapter interface and WHEP/WHIP adapters (Phase 1) ([b7646ce](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/b7646cead19898621be6df7844e40e580ba2a49a))


### Bug Fixes

* add missing Volatile import for iOS session classes ([e485728](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/e485728904757a9dc1e8898430b383229568dc8a))
* resolve JVM VideoRenderer black screen and color distortion ([194bcbd](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/194bcbd8796ee2b1f26d39a948bfc311e8989ead))
* restore onEvent callbacks and AudioPushController.start() in v2 composables ([0f668e9](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/0f668e9aa398ebae7957fe341684d4c8247efd05))
* support DataChannel creation before connect and replay state on setListener ([a658752](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/a658752c5f6f8584943f199a2e67e5a5ea6bd044))

## [1.1.0](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/compare/v1.0.1...v1.1.0) (2026-03-30)


### Features

* add migration docs ([b7fc02b](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/b7fc02b807a5256985e8102e869d15efd7395cba))
* add Unit Tests (jvmTest) ([cd14276](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/cd14276e53f2050397e0f461329d4c6a19ed9fa3))


### Bug Fixes

* should not compile file for ios simulator ([86ffa75](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/86ffa75cb12e189f5b5160ec626062078d00779e))

## [1.0.1](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/compare/v1.0.0...v1.0.1) (2026-03-26)


### Bug Fixes

* lowercase artifact name for GitHub Packages compatibility ([e2efc2e](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/e2efc2e96e2f9eb749cadf57fb914e5f5e69b394))
* lowercase artifact name for GitHub Packages compatibility ([53e42f2](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/53e42f225f228369cb5e6066e8972919d204e2c7))

## 1.0.0 (2026-03-26)


### Bug Fixes

* add gradle-wrapper.jar for CI builds ([84de0b0](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/84de0b00cd8956bef1604881a50f697ef778601e))
* increase JVM heap to 4GB for iOS framework compilation ([ef22624](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/commit/ef22624598079c266d0ecc0c562084cd865e24c2))
