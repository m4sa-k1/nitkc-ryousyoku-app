# 寮食アプリ (Nitkc Ryousyoku App)

[English Version (英語版)](README.md)

高専寮の献立をサクッと確認できるAndroidネイティブアプリです。
Web版（PWA）である [ryousyoku.m4sak1.me](https://ryousyoku.m4sak1.me/) のAndroidネイティブ版となります。

## 機能
- **データ通信**: アプリサイズ肥大化を防ぐため、データは全てネット上から取得します。データの大元は[学校公式の献立PDF](https://www.kagawa-nct.ac.jp/dormitoryE/kondate.pdf)に基づいています。
- **オフライン対応**: Coilを利用して画像を強力にキャッシュしているため、一度見た献立はオフラインでも表示できます。
- **UI再現**: PWA版の洗練されたデザインをJetpack Composeで完全再現しました。
- **リップルエフェクト無効化**: タップ時の青いハイライト（波紋効果）を無効化し、よりWeb版に近いスッキリとした操作感を実現しています。

## ダウンロード
APKファイルはGitHub Actionsにより自動ビルドされ、Releasesで公開されています。
- [最新リリースはこちら](https://github.com/m4sa-k1/nitkc-ryousyoku-app/releases/latest)

## 行動規範
本プロジェクトに参加する際は、必ず[行動規範 (Code of Conduct)](CODE_OF_CONDUCT_ja.md)をお読みください。

## ライセンス
MIT
