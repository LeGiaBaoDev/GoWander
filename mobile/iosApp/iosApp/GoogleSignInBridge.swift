import Foundation
import os
import UIKit
import GoogleSignIn
import Shared

/// Google's SDK is Swift, the rest of the app is Kotlin: this is the only bridge between them (ADR-040 §3.5).
/// It opens Google's sheet and hands the ID token back to the shared code; the server checks the token.
final class GoogleSignInBridge: NSObject, GoogleTokenProvider {
    static let shared = GoogleSignInBridge()

    /// Called once at launch, before anything can ask for a token. The client ID lives in Info.plist (GIDClientID),
    /// which is what Google's SDK reads by itself as well.
    static func configure() {
        if let clientID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String, !clientID.isEmpty {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        }
        GoogleBridge.shared.provider = GoogleSignInBridge.shared
        // One line, no personal data: proves the Kotlin side can see the provider (the Google button depends on it).
        Logger(subsystem: "com.legiabao.gowander.dev", category: "auth")
            .info("google provider registered: \(GoogleBridge.shared.provider != nil), clientID set: \(GIDSignIn.sharedInstance.configuration.map { !$0.clientID.isEmpty } ?? false)")
    }

    /// Google sends the person back to `com.googleusercontent.apps.<id>:/oauth2redirect…`; the URL scheme is registered
    /// in Info.plist. Without this call the sheet never closes on a real device.
    static func handle(_ url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    func signIn(onResult: @escaping (String?, String?) -> Void) {
        guard let presenting = Self.topViewController() else {
            onResult(nil, "no view controller to show Google's sheet")
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenting) { result, error in
            if let error {
                let nsError = error as NSError
                if nsError.domain == kGIDSignInErrorDomain, nsError.code == GIDSignInError.canceled.rawValue {
                    onResult(nil, nil) // the person closed the sheet: not a failure
                } else {
                    onResult(nil, error.localizedDescription)
                }
                return
            }
            guard let token = result?.user.idToken?.tokenString, !token.isEmpty else {
                onResult(nil, "Google did not return an ID token")
                return
            }
            onResult(token, nil)
        }
    }

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
