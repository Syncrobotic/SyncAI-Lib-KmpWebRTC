# Changelog

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
