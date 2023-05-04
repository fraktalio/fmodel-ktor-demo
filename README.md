## Running

### Jaeger

```shell
docker run -d --name jaeger \
-e COLLECTOR_ZIPKIN_HOST_PORT=:9411 \
-e COLLECTOR_OTLP_ENABLED=true \
-p 6831:6831/udp \
-p 6832:6832/udp \
-p 5778:5778 \
-p 16686:16686 \
-p 4317:4317 \
-p 4318:4318 \
-p 14250:14250 \
-p 14268:14268 \
-p 14269:14269 \
-p 9411:9411 \
jaegertracing/all-in-one:1.38
```

Note, this run command was taken from
the [Jaeger Docs](https://www.jaegertracing.io/docs/1.6/getting-started/#all-in-one-docker-image)
and probably opens unnecessary ports for this example.

Once running, you can view the UI at http://localhost:16686/.

### Application

```shell
gradle run
```

Build fat jar:

```shell
./gradlew buildFatJar
```

Run the jar from terminal:

```shell
java -Dio.ktor.development=true -javaagent:./opentelemetry-javaagent.jar -Dotel.service.name=fmodel-ktor-demo -Dotel.traces.exporter=jaeger -Dotel.exporter.jaeger.endpoint=http://localhost:14250 -Dotel.metrics.exporter=none -Dotel.instrumentation.experimental.span-suppression-strategy=none -jar build/libs/fmodel-ktor-demo-all.jar
```

### Resources

#### Tracing (Open Telemetry)

- [https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/ktor/ktor-2.0/library](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/ktor/ktor-2.0/library)
- [https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/7755](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/7755)
- [https://github.com/mpeyper/open-telemetry-coroutine-tracing-repro/tree/main/src/main/kotlin/example](https://github.com/mpeyper/open-telemetry-coroutine-tracing-repro/tree/main/src/main/kotlin/example)

