import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Google sign-in (ADR-040 §3.5): client ID from Info.plist, and the bridge the Kotlin side asks for.
        GoogleSignInBridge.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Google returns to com.googleusercontent.apps.<id>:/oauth2redirect… (the scheme is in Info.plist).
                .onOpenURL { url in _ = GoogleSignInBridge.handle(url) }
        }
    }
}
