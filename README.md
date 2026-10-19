# Balaio API Gateway

Ponto de entrada HTTP do frontend para os microsserviços. O gateway encaminha chamadas e aplica políticas de borda; regras de negócio e acesso ao banco pertencem aos serviços de domínio.

## Rotas

| Método e rota pública | Destino | Estado |
| --- | --- | --- |
| GET /api/v1/pecas | catalog-service | Ativa nesta primeira fatia |
| GET /api/v1/pecas/{slug} | catalog-service | Planejada |
| GET /api/v1/referencias | catalog-service | Planejada |
| /api/v1/auth/**, /api/v1/usuario/**, /api/v1/artesaos/**, /api/v1/coletivos/** | profile-service | Planejadas |
| /api/v1/checkout, /api/v1/pedidos/**, /api/v1/estado/**, /api/v1/fretes | commerce-service | Planejadas |
| /api/v1/pecas/{slug}/perguntas, /api/v1/videos/**, /api/v1/admin/mediacoes/** | community-service | Planejadas |
| /api/v1/eventos/** | events-service | Planejada |

As rotas mantêm o prefixo e os parâmetros da Fake API. Operações sociais relacionadas a pedidos pertencem a community-service; os dados transacionais do pedido pertencem a commerce-service.

## Configuração local

Java 25, Maven Wrapper, Spring Boot 4.1.1 e Spring Cloud Gateway Server WebMVC. Porta padrão: 8080.

| Variável | Padrão |
| --- | --- |
| GATEWAY_PORT | 8080 |
| CATALOG_SERVICE_URL | http://localhost:8081 |

No Windows: .\mvnw.cmd spring-boot:run. Em Linux/macOS: ./mvnw spring-boot:run.

Teste: curl "http://localhost:8080/api/v1/pecas?q=leao&ordenar=preco-asc"
Health: http://localhost:8080/actuator/health
Frontend: API_GATEWAY_URL=http://localhost:8080/api/v1

Os endereços locais compartilhados e a execução integrada serão consolidados no repositório platform. Não versionar segredos nem acessar diretamente os bancos dos serviços.
