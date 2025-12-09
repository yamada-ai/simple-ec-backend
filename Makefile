.PHONY: help build test lint check verify clean run db-up db-down db-logs seed seed-small seed-large seed-fixed truncate summary

help:
	@echo "Available targets:"
	@echo ""
	@echo "Build & Test:"
	@echo "  make build       - ビルド（テスト除く）"
	@echo "  make test        - テスト実行"
	@echo "  make lint        - detekt実行"
	@echo "  make check       - ビルド + テスト + detekt（CI相当）"
	@echo "  make verify      - check + 起動確認 + API動作確認"
	@echo "  make clean       - ビルド成果物削除"
	@echo ""
	@echo "Run:"
	@echo "  make run         - アプリケーション起動"
	@echo ""
	@echo "Database:"
	@echo "  make db-up       - データベース起動"
	@echo "  make db-down     - データベース停止"
	@echo "  make db-logs     - データベースログ表示"
	@echo ""
	@echo "Data Management (requires running app):"
	@echo "  make seed        - テストデータ投入（100顧客、1000注文）"
	@echo "  make seed-small  - 少量データ投入（5顧客、10注文）"
	@echo "  make seed-large  - 大量データ投入（1000顧客、10000注文）"
	@echo "  make seed-fixed  - 固定シードでデータ投入（再現可能）"
	@echo "  make truncate    - 全データ削除"
	@echo "  make summary     - データ概要表示"

build:
	./gradlew build -x test

test:
	./gradlew test

lint:
	./gradlew detekt

check: build test lint

verify:
	@echo "Running check..."
	@./gradlew --no-daemon --console=plain bootRun & \
	PID=$$!; \
	echo "Waiting for server to start..."; \
	sleep 5; \
	echo "Checking API (swagger-ui)"; \
	curl --fail --head --silent http://localhost:8080/swagger-ui.html || (echo "API check failed"; kill $$PID; exit 1); \
	echo "API check succeeded"; \
	kill $$PID || true

clean:
	./gradlew clean

# Run application
run:
	./gradlew bootRun

# Database management
db-up:
	docker compose up -d

db-down:
	docker compose down

db-logs:
	docker compose logs -f postgres

# Data management (Admin API)
seed:
	@echo "Seeding data (100 customers, 1000 orders)..."
	@curl -s -X POST "http://localhost:8080/admin/seed?customers=100&orders=1000" | jq '.'

seed-small:
	@echo "Seeding small data (5 customers, 10 orders)..."
	@curl -s -X POST "http://localhost:8080/admin/seed?customers=5&orders=10" | jq '.'

seed-large:
	@echo "Seeding large data (1000 customers, 10000 orders)..."
	@curl -s -X POST "http://localhost:8080/admin/seed?customers=1000&orders=10000" | jq '.'

seed-fixed:
	@echo "Seeding data with fixed seed (100 customers, 1000 orders, seed=12345)..."
	@curl -s -X POST "http://localhost:8080/admin/seed?customers=100&orders=1000&seed=12345" | jq '.'

truncate:
	@echo "Truncating all data..."
	@curl -s -X DELETE "http://localhost:8080/admin/truncate" | jq '.'

summary:
	@echo "Data summary:"
	@curl -s "http://localhost:8080/admin/summary" | jq '.'
