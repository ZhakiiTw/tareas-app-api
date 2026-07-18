.PHONY: build up down logs status deploy backup

build:
	docker compose build

up:
	docker compose up -d

down:
	docker compose down

logs:
	./scripts/logs.sh

status:
	./scripts/status.sh

deploy:
	./scripts/deploy.sh

backup:
	./scripts/backup-db.sh
