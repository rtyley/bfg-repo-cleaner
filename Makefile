DOCKER_TAG=bfg-repo-cleaner

build:
	docker build -t $(DOCKER_TAG) .

extract:
	container_id=`docker run -d $(DOCKER_TAG) sh -c "sleep 60"` && \
	echo $$container_id && \
	dir_name=`docker exec $$container_id sh -c 'ls /usr/src/app/target/ | grep scala- | head -n1'` && \
	echo $$dir_name && \
	mkdir -p ./target/$$dir_name && \
	docker cp $$container_id:/usr/src/app/target/$$dir_name ./target/ && \
	docker rm -f $$container_id

_dev_rebuild: ## Faster in-host rebuild, yet modifies the host state
	docker run --rm -v "$(PWD):/usr/src/app" $(DOCKER_TAG) sbt assembly

.PHONY: build run

