# Balaio API Gateway

Entrada HTTP do frontend para os cinco microsserviços. O gateway encaminha o caminho, o método, a query e os cabeçalhos recebidos. Cada serviço implementa seus contratos e controla seus dados.

## Destinos configurados

| Serviço | URL local padrão | Caminhos da Fake API encaminhados |
| --- | --- | --- |
| catalog-service | http://localhost:8081 | Peças, histórico e relacionadas, peças do artesão, referências e arquivos de fotos |
| profile-service | http://localhost:8082 | Auth, usuário, conta, artesãos, coletivos, selo, curadoria e histórico de busca |
| commerce-service | http://localhost:8083 | Checkout, fretes, pedidos, pedidos pendentes, vendas e sacola |
| community-service | http://localhost:8084 | Perguntas, conversas, avaliações, vídeos, mediações, favoritos e estado dos vídeos |
| events-service | http://localhost:8085 | Eventos |

Todas as rotas públicas conservam o prefixo `/api/v1`. O catálogo já implementa `GET /api/v1/pecas`. Os outros encaminhamentos apenas preparam a borda: até que o respectivo serviço implemente a rota, a resposta será de rota inexistente nesse serviço.

Os caminhos compartilhados são específicos para evitar conflito: `/pecas/{slug}/perguntas` vai para comunicação e as demais rotas de peça vão para catálogo; `/pedidos/{id}/conversa` e `/pedidos/{id}/avaliacao` vão para comunicação e as rotas transacionais de pedido vão para comércio. `GET /api/v1/estado` ainda precisa de um contrato de composição entre sacola, favoritos e histórico antes de ser encaminhado; as rotas `PUT /estado/...` já apontam para os respectivos donos.

O webhook do Checkout Pro usa apenas `POST /api/v1/pagamentos/mercado-pago/notificacoes`, encaminhado ao commerce com query `data.id` e cabeçalhos `x-signature`/`x-request-id` intactos. A URL pública configurada no Mercado Pago precisa ser HTTPS e alcançável pelo provedor; o commerce valida a assinatura antes de persistir a notificação. Nenhuma rota interna de serviço é exposta pelo gateway.

As rotas de recomendações por IA e indicadores administrativos ficam para uma etapa posterior, conforme o escopo atual. Ao criar uma rota nova, adicione o caminho ao destino correto em `src/main/resources/application.yaml` e verifique o contrato em `Balaio/Frontend/src/services/api` e `Balaio/Frontend/fake-api/server.cjs`.

## Execução local

Java 25, Maven Wrapper, Spring Boot 4.1.1 e Spring Cloud Gateway Server WebMVC. Porta padrão: 8080.

| Variável | Padrão |
| --- | --- |
| GATEWAY_PORT | 8080 |
| CATALOG_SERVICE_URL | http://localhost:8081 |
| PROFILE_SERVICE_URL | http://localhost:8082 |
| COMMERCE_SERVICE_URL | http://localhost:8083 |
| COMMUNITY_SERVICE_URL | http://localhost:8084 |
| EVENTS_SERVICE_URL | http://localhost:8085 |

No Windows: `.\mvnw.cmd spring-boot:run`. Em Linux/macOS: `./mvnw spring-boot:run`.

Teste: `curl "http://localhost:8080/api/v1/pecas?q=leao&ordenar=preco-asc"`

Health: http://localhost:8080/actuator/health

Frontend: `API_GATEWAY_URL=http://localhost:8080/api/v1`

O Compose integrado e a escolha dos serviços a iniciar estão em `platform/docker-compose.yml`. O gateway inicia mesmo quando um serviço opcional está parado; as chamadas destinadas a ele só terão resposta quando o serviço estiver disponível.
