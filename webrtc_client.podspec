Pod::Spec.new do |spec|
    spec.name                     = 'webrtc_client'
    spec.version                  = '1.0.0'
    spec.homepage                 = 'https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'Kotlin Multiplatform WebRTC Client SDK'
    spec.vendored_frameworks      = 'build/cocoapods/framework/KotlinWebRTCClient.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target    = '15.0'
    spec.dependency 'GoogleWebRTC', '1.1.31999'
    if !Dir.exist?('build/cocoapods/framework/KotlinWebRTCClient.framework') || Dir.empty?('build/cocoapods/framework/KotlinWebRTCClient.framework')
        raise "
        Kotlin framework 'KotlinWebRTCClient' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:
            ./gradlew :webrtc-client:generateDummyFramework
        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':webrtc-client',
        'PRODUCT_MODULE_NAME' => 'KotlinWebRTCClient',
    }
    spec.script_phases = [
        {
            :name => 'Build webrtc_client',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                    echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                    exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
    spec.resources = ['build/compose/cocoapods/compose-resources']
end
