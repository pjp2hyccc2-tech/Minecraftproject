# データスキーマ

## 1. データの分類（企画書「データ分離」）

| 分類 | 例 | シーズンリセット | 現在の保存先 |
|---|---|---|---|
| ① アカウント永続データ | UUID、最終名、初回/最終ログイン、ログイン回数（将来: 称号・コスメ・実績・設定） | されない | `accounts/<uuid>.json` |
| ② キャラクターのシーズンデータ | レベル、EXP、累計EXP、ステータス割り振り（将来: 職業・装備・所持品・通貨・クエスト） | 設定に従う（D-005） | `seasons/<seasonId>/characters/<uuid>.json` |
| ③ シーズン終了後の記録 | 確定ランキング、ボス討伐記録、英雄の記録 | 永久保存 | 未実装（Phase 6/10） |

すべてのシーズンデータは `season_id` を持ち、保存場所もシーズンごとに物理的に分かれる（Phase 1 から）。

## 2. 現行ファイルストア（Phase 1）

```
<ワールド>/eternalrealm/
├── accounts/<uuid>.json            (+ .prev)
├── seasons/<seasonId>/characters/<uuid>.json   (+ .prev)
├── quarantine/<元の名前>.<UTC時刻>.corrupt
└── emergency/<uuid>-<seasonId>-<UTC時刻>.json    … 停止時に保存できなかったデータ（通常と同じ形式）
<ワールド>/eternalrealm_backups/eternalrealm-<UTC時刻>.zip
```

### 2.1 キャラクター（schema 1）

```json
{
  "schema": 1,
  "playerId": "11111111-2222-3333-4444-555555555555",
  "seasonId": "s1",
  "level": 7,
  "experience": 123,
  "totalExperience": 4567,
  "allocatedStats": { "str": 4, "vit": 0, "agi": 0, "int": 0, "dex": 0, "luk": 2 },
  "createdAt": "2026-11-01T10:00:00Z",
  "updatedAt": "2026-11-08T12:34:56.789Z"
}
```

| 項目 | 型 | 制約 |
|---|---|---|
| `schema` | int | 1。より大きい値は読み込み拒否（IncompatibleData） |
| `playerId` | UUID文字列 | ファイル名のUUIDと一致すること |
| `seasonId` | 文字列 | `[a-z0-9][a-z0-9_-]{0,31}`、保存ディレクトリと一致 |
| `level` | int | 1〜100 |
| `experience` | long | 0以上（現在レベル内の進捗。ルールで再検証） |
| `totalExperience` | long | 0以上（シーズン累計。ランキング用） |
| `allocatedStats` | object | 6種すべて必須、未知キー禁止、各0〜9999 |
| `createdAt`, `updatedAt` | ISO-8601 UTC | — |

未割り振りポイントは保存しない（レベルと割り振りから導出。D-012）。

**schema 2・3 の追加フィールド**（現在の書き出しは schema 3。古い schema は読み込み時に既定値で移行）:

| 項目 | 型 | 内容 |
|---|---|---|
| `jobId` | 文字列 | 職業（schema 2） |
| `skills` | object | スキルID → 投資ポイント（schema 2）。読み込み時・Lv変更時に前提・必要Lvを再検証し、合わなければ返金 |
| `gold` | long | 所持金（schema 2） |
| `extensions` | object | 各モジュールのデータ（schema 2） |
| `skillLoadout` | 文字列の配列 | **schema 3**。スキル枠 1〜6 のスキルID（空文字 = 空き、最大16件）。空配列 = 既定の枠（初期スキル）。読み込み時に `SkillTree.sanitize` で検証 |

### 2.2 アカウント（schema 1）

| 項目 | 型 |
|---|---|
| `schema` | 1 |
| `playerId` | UUID |
| `lastKnownName` | 文字列（最大64、表示用） |
| `firstLoginAt`, `lastLoginAt` | ISO-8601 UTC |
| `loginCount` | long |

### 2.3 スキーマ変更のルール

1. 変更時は schema 番号を上げ、`JsonCodecs.checkSchema` の後に旧版からの移行処理を追加する。
2. 新しいビルドは古い schema を読める。古いビルドは新しい schema を拒否する（破損防止）。
3. 移行を含むリリースの前に必ずスナップショットを取る。

## 3. PostgreSQL 目標スキーマ（設計案・未実装）

企画書のテーブル一覧のうち、Phase 1 のデータに対応する部分。残りは各フェーズで具体化する。

```sql
CREATE TABLE accounts (
    player_id       UUID PRIMARY KEY,
    last_known_name VARCHAR(64) NOT NULL,
    first_login_at  TIMESTAMPTZ NOT NULL,
    last_login_at   TIMESTAMPTZ NOT NULL,
    login_count     BIGINT      NOT NULL CHECK (login_count >= 0)
);

CREATE TABLE seasons (
    season_id   VARCHAR(32) PRIMARY KEY CHECK (season_id ~ '^[a-z0-9][a-z0-9_-]{0,31}$'),
    state       VARCHAR(16) NOT NULL,          -- PREPARATION / ACTIVE / FINAL_BATTLE / RESULTS / RESETTING / COMPLETED
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL
);

CREATE TABLE season_characters (                -- 企画書の characters + character_stats に相当
    season_id        VARCHAR(32) NOT NULL REFERENCES seasons,
    player_id        UUID        NOT NULL REFERENCES accounts,
    level            SMALLINT    NOT NULL CHECK (level BETWEEN 1 AND 100),
    experience       BIGINT      NOT NULL CHECK (experience >= 0),
    total_experience BIGINT      NOT NULL CHECK (total_experience >= 0),
    stat_str SMALLINT NOT NULL DEFAULT 0, stat_vit SMALLINT NOT NULL DEFAULT 0,
    stat_agi SMALLINT NOT NULL DEFAULT 0, stat_int SMALLINT NOT NULL DEFAULT 0,
    stat_dex SMALLINT NOT NULL DEFAULT 0, stat_luk SMALLINT NOT NULL DEFAULT 0,
    revision         BIGINT      NOT NULL DEFAULT 0,   -- 楽観的ロック（複数サーバー時）
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (season_id, player_id)
);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    season_id   VARCHAR(32),
    player_id   UUID,
    actor       VARCHAR(64) NOT NULL,         -- player / admin名 / system
    action      VARCHAR(64) NOT NULL,         -- exp_grant, level_set, stats_reset ...
    detail      JSONB       NOT NULL
);
CREATE INDEX audit_logs_player_idx ON audit_logs (player_id, occurred_at);
```

今後のフェーズで追加予定: `character_skills`, `character_equipment`, `character_inventory`, `character_quests`,
`character_achievements`, `character_currency`, `season_progress`, `season_world_state`, `season_boss_records`,
`parties`, `party_members`, `guilds`, `guild_members`, `ranking_records`, `ranking_snapshots`, `boss_records`,
`dungeon_records`, `market_listings`, `item_transactions`, `economy_logs`, `admin_logs`。
通貨・アイテムの移動は**単一トランザクション＋監査ログ**を必須とする（複製防止）。

## 4. ファイル→DB 移行（Phase 1.5 予定）

サーバー停止中に `eternalrealm/` を走査し、各JSONを検証してDBへ投入するコマンドラインツールを用意する（冪等: 既存行は `updated_at` が新しい方を採用）。
