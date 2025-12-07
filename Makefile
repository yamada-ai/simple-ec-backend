.PHONY: help build test lint check verify clean

help:
	@echo "Available targets:"
	@echo "  make build       - ビルド（テスト除く）"
	@echo "  make test        - テスト実行" 
	@echo "  make lint        - detekt実行"
	@echo "  make check       - ビルド + テスト + detekt（CI相当）"
	@echo "  make verify      - check + 起動確認 + API動作確認"
	@echo "  make clean       - ビルド成果物削除"

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
