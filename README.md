# Balaio API Gateway

Ponto de entrada HTTP do frontend para os microsserviços do Balaio. O gateway encaminha requisições e aplica políticas de borda que forem acordadas; regras de negócio e acesso ao banco pertencem aos serviços de domínio.

## Estado atual

Este repositório contém o esqueleto Spring Boot e a dependência Spring Cloud Gateway Server WebMVC. Ainda não há rotas, filtros, autenticação ou endpoints de negócio configurados. A primeira integração planejada é a consulta pública do catálogo.

## Responsabilidade e rotas planejadas

O contrato do frontend usa o prefixo `/api/v1` e o envelope JSON `{ "dados": ..., "erro": null }`; listagens paginadas também retornam `paginacao`. A [Fake API do frontend](https://github.com/lucaschavessf2/Balaio/blob/main/Frontend/docs/api.md) é a referência do contrato existente.

| Rota pública planejada | Destino | Situação |
| --- | --- | --- |
| `GET /api/v1/pecas` | `catalog-service` | Primeira fatia; ainda sem rota configurada |
| `GET /api/v1/pecas/{slug}` | `catalog-service` | Primeira fatia; ainda sem rota configurada |
| `GET /api/v1/referencias` | `catalog-service` | Referências para filtros do catálogo; ainda sem rota configurada |
| `/api/v1/auth/**`, `/usuario/**`, `/artesaos/**`, `/coletivos/**` | `profile-service` | Planejado |
| `/api/v1/checkout`, `/pedidos/**`, `/estado/**`, `/fretes` | `commerce-service` | Planejado; operações sociais de pedido podem pertencer a `community-service` |
| `/api/v1/pecas/{slug}/perguntas`, `/videos/**`, `/admin/mediacoes/**` | `community-service` | Planejado |
| `/api/v1/eventos/**` | `events-service` | Planejado |

A tabela descreve o destino pretendido, não rotas já ativas. Ao configurar regras sobrepostas, encaminhe operações de conversa e avaliação de pedido para o serviço responsável pela comunidade, preservando os detalhes do pedido no serviço de comércio.

## Regras para o gateway

- Manter o prefixo e os payloads consumidos pelo frontend sempre que possível.
- Encaminhar cada requisição ao serviço dono do dado; não consultar diretamente bancos de dados.
- Manter regras de negócio, validação do domínio e persistência nos serviços.
- Configurar endereços dos serviços por ambiente, com valores locais fornecidos pelo repositório `platform`; não versionar segredos.
- Restringir CORS, autenticação, limites e timeouts conforme requisitos definidos. Nada disso está configurado ainda.

## Stack

- Java 25, Maven Wrapper e Spring Boot 4.1.1.
- Spring Cloud Gateway Server WebMVC.
- Spring Cloud BOM 2025.1.3, compatível com a linha Spring Boot 4.1.x segundo as [versões suportadas do Spring Cloud](https://spring.io/projects/spring-cloud/).

## Executar e verificar

Requer JDK 25. No Windows:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
```

Em Linux/macOS:

```bash
./mvnw spring-boot:run
./mvnw test
```

O `application.yaml` atual define somente o nome da aplicação. O gateway ainda não tem destinos configurados; a integração local deverá usar os endereços definidos no `platform`.

## OpenAPI / Swagger UI

A decisão anterior não incluiu Swagger e esta dependência ainda não está no `pom.xml`. A recomendação para a implementação é documentar os contratos com OpenAPI e disponibilizar Swagger UI no gateway e nos serviços quando as rotas forem criadas. Para Spring Boot 4, a documentação do projeto springdoc indica a linha 3.x; a versão estável consultada é 3.1.1. A interface costuma ficar em `/swagger-ui/index.html`, e o documento JSON em `/v3/api-docs`. A configuração e a exposição dessas rotas ainda precisam ser implementadas.

## Referências

- [Frontend e Fake API](https://github.com/lucaschavessf2/Balaio)
- [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway)
- [springdoc-openapi](https://springdoc.org/)
