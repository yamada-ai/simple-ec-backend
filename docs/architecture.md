# Architecture

このリポジトリは検証用の小規模 EC バックエンドだが、実装の責務は以下の層に分ける。

## Layers

| Layer | Package | Responsibility |
| --- | --- | --- |
| Domain | `com.example.ec.domain` | エンティティ、値オブジェクト、リポジトリ契約、ドメイン寄りの読み取りモデル |
| Application | `com.example.ec.application` | ユースケース、アプリケーションサービス、トランザクション境界、アプリケーション例外 |
| Infrastructure | `com.example.ec.infrastructure` | jOOQ / DB など外部技術への接続、リポジトリ実装 |
| Presentation | `com.example.ec.presentation` | HTTP controller、OpenAPI model 変換、レスポンス生成、例外の HTTP 変換 |

## Dependency Rules

- `domain` は `application` / `infrastructure` / `presentation` に依存しない。
- `application` は `infrastructure` / `presentation` に依存しない。
- `infrastructure` は `application` / `presentation` に依存しない。
- `presentation` は外側の adapter なので、`application` を呼び出して HTTP 表現に変換する。

これらのルールは `LayerDependencyTest` で import ベースに検査する。

## Presentation Split

- `controller`: request を受け取り、use case / service を呼び出す。
- `mapper`: domain / application DTO を OpenAPI 生成 model に変換する。
- `streaming`: CSV など HTTP streaming response の共通処理を持つ。
- `support`: presentation 層だけで使う小さな変換関数を持つ。

controller には HTTP ルーティング、入力の取り出し、レスポンス返却以上の処理を増やさない。
