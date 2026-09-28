node_modules:
	npm install

shadow: node_modules
	npx shadow-cljs watch app

tailwind: node_modules
	npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch

test:
	clojure -M:dev -m kaocha.runner

.PHONY: shadow tailwind test
