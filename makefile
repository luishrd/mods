.PHONY: build deploy build-deploy

build:
	./gradlew build

deploy: build
	./gradlew deploySandbox
	@echo "✓ Build and deploy complete"
