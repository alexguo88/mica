/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.dreamlu.net).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dreamlu.mica.reactive.logger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.StringPool;
import net.dreamlu.mica.core.utils.StringUtil;
import net.dreamlu.mica.launcher.MicaLogLevel;
import net.dreamlu.mica.props.MicaRequestLogProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * webflux 日志请求记录，方便开发调试。
 *
 * <p>
 * 注意：暂时不支持结构体打印，想实现，请看下面的链接。
 * https://stackoverflow.com/questions/45240005/how-to-log-request-and-response-bodies-in-spring-webflux
 * https://github.com/Silvmike/webflux-demo/blob/master/tests/src/test/java/ru/hardcoders/demo/webflux/web_handler/filters/logging
 * </p>
 *
 * @author L.cm
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveRequestLogFilter implements WebFilter {
	private final MicaRequestLogProperties properties;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		MicaLogLevel level = properties.getLevel();
		// 日志没有开启直接放行
		if (MicaLogLevel.NONE == level) {
			return chain.filter(exchange);
		}

		ServerHttpRequest request = exchange.getRequest();
		// 打印请求路径
		String path = request.getPath().pathWithinApplication().value();
		// 排除请求
		if (excludedRequestPath(path)) {
			return chain.filter(exchange);
		}

		MultiValueMap<String, String> queryParams = request.getQueryParams();
		String requestURI = UriComponentsBuilder.fromPath(path).queryParams(queryParams).build().toUriString();

		// 构建成一条长 日志，避免并发下日志错乱
		StringBuilder logBuilder = new StringBuilder(500);
		// 日志参数
		List<Object> logArgs = new ArrayList<>();
		// 打印路由
		logBuilder.append("\n===> {}: {}\n");
		// 参数
		String requestMethod = request.getMethodValue();
		logArgs.add(requestMethod);
		logArgs.add(requestURI);

		// 打印请求头
		if (MicaLogLevel.HEADERS.lte(level)) {
			HttpHeaders headers = request.getHeaders();
			headers.forEach((headerName, headerValue) -> {
				logBuilder.append("===Headers===  {} : {}\n");
				logArgs.add(headerName);
				logArgs.add(StringUtil.join(headerValue));
			});
		}
		// 打印执行时间
		long startNs = System.nanoTime();
		try {
			return chain.filter(exchange);
		} finally {
			long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
			// webflux，这里打印的时间没太大意义
			logBuilder.append("<=== {}: {} ({} ms)");
			logArgs.add(requestMethod);
			logArgs.add(requestURI);
			logArgs.add(tookMs);
			log.info(logBuilder.toString(), logArgs.toArray());
		}

	}

	/**
	 * 需要排除的部分请求
	 *
	 * <p>
	 * 规范：
	 * 1. 含有 . 而非 .json，排除掉。
	 * </p>
	 */
	private boolean excludedRequestPath(String path) {
		return path.contains(StringPool.DOT) && !path.toLowerCase().endsWith(".json");
	}
}
