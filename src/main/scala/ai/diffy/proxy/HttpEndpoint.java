package ai.diffy.proxy;

import ai.diffy.BaseUrl;
import ai.diffy.Downstream;
import ai.diffy.HostPort;
import ai.diffy.functional.endpoints.Endpoint;
import ai.diffy.functional.endpoints.IndependentEndpoint;
import ai.diffy.functional.topology.Async;
import ai.diffy.transformations.TransformationEdge;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServerRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class HttpEndpoint extends IndependentEndpoint<HttpRequest, HttpResponse> {
    private static final Function<HttpServerRequest, CompletableFuture<HttpRequest>> requestBuffer = (req) -> {
        if(req.isMultipart()){
            throw new RuntimeException("Content-Type : multipart/form-data is not supported");
        }
        if(req.isFormUrlencoded() &&
                HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED
                        .contentEquals(req.requestHeaders().get(HttpHeaderNames.CONTENT_TYPE))){
            throw new RuntimeException("Content-Type : application/x-www-form-urlencoded is not supported");
        }
        return req.receive().aggregate().asString().toFuture().thenApply(body -> new HttpRequest(
            req.method().name(),
            req.uri(),
            req.path(),
            req.params(),
            req.requestHeaders(),
            body,
            TransformationEdge.all.toString()
        ));
    };

    public static final Endpoint<HttpServerRequest, CompletableFuture<HttpRequest>> RequestBuffer =
            Async.contain(Endpoint.from("RequestBuffer", () -> requestBuffer));
    public HttpEndpoint(String name, HttpClient client) {
        this(name, client, Collections.emptyMap());
    }

    public HttpEndpoint(String name, HttpClient client, Map<String, String> extraHeaders) {
        super(name, () -> (HttpRequest req) ->
            client
                .headers(headers -> {
                    HashMap<String, String> mapHeaders = new HashMap<>(req.getHeaders());
                    mapHeaders.putAll(extraHeaders);
                    headers.add(HttpMessage.toHttpHeaders(mapHeaders));
                })
                .request(HttpMethod.valueOf(req.getMethod()))
                .uri(req.getUri())
                .send(ByteBufMono.fromString(Mono.justOrEmpty(req.getBody())))
                .responseSingle(
                    (headers, body) ->
                        body.asString()
                            .map(b -> new HttpResponse(headers.status().toString(), headers.responseHeaders(), b))
                ).block()
        );
    }
    public Endpoint<HttpServerRequest, CompletableFuture<HttpResponse>> withSeverRequestBuffer(){
        return Endpoint.from(this.getName(), () -> (serverRequest -> requestBuffer.apply(serverRequest).thenApply(this::apply)));
    }
    private static HttpEndpoint from(String name, String host, int port, int maxHeader, Map<String, String> extraHeaders) {
        final HttpClient client = HttpClient
                .create().host(host).port(port)
                .httpResponseDecoder(httpResponseDecoderSpec ->
                        httpResponseDecoderSpec
                                .maxHeaderSize(maxHeader));
        return new HttpEndpoint(name, client, extraHeaders);
    }
    private static HttpEndpoint from(String name, String baseUrl, int maxHeader, Map<String, String> extraHeaders) {
        HttpClient client = HttpClient
                .create().baseUrl(baseUrl)
                .httpResponseDecoder(httpResponseDecoderSpec ->
                        httpResponseDecoderSpec
                                .maxHeaderSize(maxHeader));
        return new HttpEndpoint(name, client, extraHeaders);
    }

    public static HttpEndpoint from(String name, Downstream downstream, int maxHeader) {
        return from(name, downstream, maxHeader, Collections.emptyMap());
    }

    public static HttpEndpoint from(String name, Downstream downstream, int maxHeader, Map<String, String> extraHeaders) {
        if(downstream instanceof BaseUrl){
            return from(name, ((BaseUrl) downstream).baseUrl(), maxHeader, extraHeaders);
        }
        HostPort hostport = (HostPort)downstream;
        return from(name, hostport.host(), hostport.port(), maxHeader, extraHeaders);
    }
}
