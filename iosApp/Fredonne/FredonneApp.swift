import SwiftUI
import UIKit
import Shared

@main
struct FredonneApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
        }
    }
}

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
