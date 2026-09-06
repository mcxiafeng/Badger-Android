import SwiftUI
import shared

@main
struct BadgerApp: App {
    init() {
        // KMP bootstrap：初始化 Koin 容器 + 注册 BGTask handler
        // 必须在 didFinishLaunching 结束前完成（BGTaskScheduler 时序约束）
        IosAppBootstrapKt.initializeIosApp()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// SwiftUI 壳：持有 ComposeUIViewController，全屏渲染。
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
