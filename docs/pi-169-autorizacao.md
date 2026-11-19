# PI-169 — Implementacao e evidencias (19/11/2026)

## Escopo e fontes

Seguranca das operacoes existentes em Gateway, Profile, Catalog, Commerce e
Community. Jira consultado pelo conector Atlassian: PI-169, PI-168, PI-20,
PI-170, PI-172, PI-177, PI-207, PI-210, PI-214, PI-249, PI-261 e PI-163.
Os READMEs, a arquitetura do frontend, as configuracoes, os controllers e os
testes foram confrontados. O unico AGENTS.md encontrado fica no frontend;
nenhum arquivo desse repositorio foi alterado. Nao foi fornecida uma pagina
Confluence especifica; nenhuma informacao de Confluence foi presumida.

OpenAPI gerado consultado no ambiente atualizado: Catalog 3.1.0/12 caminhos,
Community 3.1.0/18 caminhos. Community declara bearer-jwt; Catalog ainda nao
declara securitySchemes. Essa consolidacao documental permanece no PI-261;
a ausencia da anotacao nao abre a cadeia de seguranca real.

## Branches e arquivos

Todas as branches PI-169 partem das referencias develop locais/remotas
inspecionadas, sem fetch, merge, cherry-pick, reset ou stash. Nenhum commit,
push, PR ou atualizacao do Jira foi realizado.

- Gateway: feat/pi-169-gateway-autorizacao, base ec9e07b. GatewayAccessPolicy,
  ApiGatewayApplicationTests, README e este documento.
- Profile: feat/pi-169-profile-autorizacao, base 1adaab1. AutenticacaoWebConfig,
  OriginAutenticacaoInterceptor, OriginAutenticacaoInterceptorTest,
  PerfilArtesaoServiceTest, novo PerfilOriginHttpTest e README.
- Catalog: feat/pi-169-catalog-autorizacao, base 1468216. JwtCatalogSecurityConfig,
  PecasController, PecasControllerIntegrationTest, novo ValidadorClaimsJwt,
  novos JwtContractTest/JwtHttpTest e README.
- Commerce: feat/pi-169-commerce-autorizacao, base 913155c.
  JwtCommerceSecurityConfig, novo ValidadorClaimsJwt, novos
  JwtContractTest/JwtHttpTest e README.
- Community: feat/pi-169-community-autorizacao, base dea49b9. SecurityConfig,
  ChavePublicaJwtLoader, novo ValidadorClaimsJwt, novos JwtContractTest/JwtHttpTest
  e README.
- Events: develop ae8233e, sem alteracoes. Nao possui controllers HTTP de eventos.
- Platform e frontend: sem alteracoes versionadas nesta implementacao.

## Correcoes

1. Gateway: GET exato /admin/mediacoes permite comprador/admin; decisoes continuam
   administrativas. POST /auth/redefinicao e anonimo, restrito ao caminho/metodo.
   Criacao de video exige artesao; comentarios/colecoes exigem autenticacao;
   PUT da sacola exige comprador. Nao ha permissao generica para /admin/**.
2. Profile: interceptor existente passa a considerar POST, PUT, PATCH e DELETE,
   e seu registro inclui os caminhos de edicao de perfil e imagem. Somente
   adicionar os caminhos nao bastava: a develop interceptava apenas POST.
   Cookie exige Origin permitido; cliente exclusivamente Bearer pode omiti-lo.
3. Catalog/Commerce/Community: decoder de producao utiliza validacao estrita do
   JSON assinado, seguindo o comportamento ja existente no Gateway. Cada
   repositorio possui seu proprio validador, sem biblioteca compartilhada.
   Sete claims obrigatorios, tipos, UUID canonico, iss/aud, papel conhecido,
   iat ate 30 segundos no futuro, exp estrito, duracao positiva ate 3600 segundos,
   nbf, duplicatas e referencias de chave no header sao verificados localmente.
4. Leitura da chave publica fecha o stream nos tres consumidores. Os primeiros
   testes revelaram o arquivo bloqueado no Windows; nenhum teste ignora a limpeza.
5. Catalog: alias nao contratual administrador removido. Token com esse papel
   produz 401; admin reconhecido sem permissao continua recebendo 403. Nao foi
   concedido bypass de propriedade a administradores.

Emissao RS256, sete claims, 60 minutos, cookie HttpOnly/SameSite, ausencia de
refresh, Bearer, sanitizacao, CORS, envelope e guards administrativos foram
preservados. Os servicos nao consultam bancos de outros dominios.

## Matriz das operacoes existentes

Prefixo /api/v1; rotas de imagem requerem a integracao PI-214 preservada.

| Dono | Operacoes | Permissao/regra |
| --- | --- | --- |
| Profile | POST auth/cadastro, login, entrar, recuperacao, redefinicao, logout | Publicas; recuperacao valida seu proprio token; Origin quando aplicavel |
| Profile | GET usuario; POST auth/alterar-senha | Propria conta; senha atual na alteracao |
| Profile | GET artesaos e artesaos/{slug} | Publicas |
| Profile | GET/POST/PATCH artesao/me | Artesao da propria conta |
| Profile | PATCH artesaos/{slug} | Dono; excecao administrativa preexistente sem troca obrigatoria pendente |
| Profile/PI-214 | PUT/DELETE artesao/me/imagem | Artesao, propria imagem |
| Profile/PI-214 | GET/HEAD arquivos/perfis/{id} | Midia publica disponibilizada pelo Profile |
| Catalog | GET pecas, detalhe, historico, relacionadas, referencias, arquivos e pecas do artesao | Leitura publica; painel/rascunhos limitados ao dono |
| Catalog | POST pecas; PATCH/DELETE pecas/{slug} | Verificacao de papel no controller e de propriedade no caso de uso; nenhuma ampliacao administrativa |
| Commerce | GET fretes | Publico |
| Commerce | POST checkout; GET pedidos e pedido; PATCH cancelar | Comprador, pedido proprio e elegibilidade |
| Commerce | GET artesao/vendas e pedidos-pendentes; PATCH estado/recusar | Artesao dono |
| Commerce | GET/PUT estado/sacola; POST sacola/revalidar | Comprador, sub validado |
| Commerce | POST pagamentos/mercado-pago/notificacoes | Assinatura propria do webhook; sem JWT de usuario |
| Community | GET perguntas/reputacao | Publico |
| Community | POST perguntas; PATCH resposta | Autenticado para perguntar; artesao dono para responder |
| Community | GET/PUT/PATCH colecoes | Usuario autenticado, colecao propria |
| Community | GET videos, detalhe, comentarios, capa/arquivo | Publicados publicos; rascunho somente autor |
| Community | GET videos?painel=true; POST videos; PATCH/DELETE video; PUT capa/arquivo | Artesao autor |
| Community | POST comentarios | Autenticado |
| Community | POST visualizacoes | Publico |
| Community | POST/GET avaliacao do pedido | Comprador elegivel/proprietario |
| Community | GET/POST conversa; POST leitura; GET artesao/conversas | Participante/autor conforme pedido confirmado por API do Commerce |
| Community | POST admin/mediacoes | Comprador no servico; Gateway mantem autenticacao anterior |
| Community | GET admin/mediacoes | Comprador: proprias; admin: visao administrativa |
| Community | PATCH mediacao; POST decisao/reprocessar-entrega | Admin |
| Catalog/Commerce | /internal/v1 | X-Balaio-Service-Token, nao exposto pelo Gateway |
| Events | Nenhum endpoint HTTP de negocio encontrado | Organizador/submissao/aprovacao permanecem PI-210/205 |

403 identifica credencial valida sem permissao; 401 identifica ausencia ou
invalidade da credencial. Contratos que ocultam recurso de terceiro por 404
foram preservados. Admin nao e um superusuario implicito de todos os dominios.

## Testes executados

JDK 25.0.5+8, Maven Wrapper e Docker Desktop. As primeiras execucoes expuseram
problemas de fixture/ambiente e os defeitos corrigidos; os resultados abaixo
sao das execucoes finais apos as correcoes.

| Repositorio/cenario | Resultado |
| --- | --- |
| Gateway verify | 274 testes; zero falhas, erros ou ignorados |
| Profile verify | 574 testes; zero falhas, erros ou ignorados |
| Catalog verify com CATALOG_POSTGRES_TEST=true | 85 testes; zero falhas, erros ou ignorados |
| Commerce verify com COMMERCE_POSTGRES_TEST=true | 82 testes; zero falhas, erros ou ignorados |
| Community verify com COMMUNITY_POSTGRES_TEST=true | 223 testes; 221 executados, dois S3 opt-in ignorados |
| Community ArmazenamentoS3AdapterTest com COMMUNITY_S3_TEST=true | Os dois testes S3 executados separadamente e aprovados |
| Copia Gateway com PI-214 | 282 testes aprovados, inclusive oito de midia |
| Copia Profile com PI-214 | 614 testes, 613 executados sem falha e um S3 opt-in ignorado |
| Profile S3MidiaIntegrationTest com credencial consumidora local | Um teste executado separadamente e aprovado contra SeaweedFS local |
| HTTP integrado atualizado | 38 verificacoes aprovadas |
| Chrome/Playwright com reinicio | Aprovado; 45 respostas de perfil/midia observadas na origem do frontend |
| scripts/test-local-storage.ps1 | Aprovado, incluindo upload/leitura privada, rede e interfaces auxiliares |

As copias de baseline com configuracoes anteriores dos tres consumidores
falharam em nove cenarios de JWT assinado invalido por servico. Apenas o
fechamento do stream foi ajustado na copia antiga para viabilizar a limpeza
no Windows; sua politica JWT foi mantida. Isso demonstra as lacunas anteriores,
nao uma falha restante da implementacao final.

JwtContractTest testa o decoder verdadeiro e assinatura RSA, sem JwtDecoder
mockado. JwtHttpTest inicia HTTP/Servlet e a cadeia/decoder de producao com
uma probe somente de teste, sem banco. Os testes existentes com Mockito e
PostgreSQL validam as regras reais de negocio, propriedade e transacoes.
A probe nao e um endpoint de producao nem substitui testes dos controllers reais.

A integracao Docker verificou 401 sem token/adulterado nos quatro servicos e
Gateway; 403 para comprador em escrita de artesao/admin; consulta de mediacoes
por comprador/admin; isolamento de colecoes; recusa de edicao de outro artesao;
Origin ausente/externo/permitido; Bearer sem Origin; token de recuperacao
invalido recusado no Profile depois de atravessar o Gateway sem sessao.
Nao foi realizado pagamento externo de sandbox como requisito de seguranca.

O navegador confirmou login HttpOnly, upload multipart, avatares da conta e
painel, perfil publico anonimo, hashes, GET/HEAD, substituicao com URL antiga
404, remocao/placeholder e leitura apos reinicio de Profile/SeaweedFS. As contas
e objetos exclusivos das fixtures foram removidos.

## Operacao segura e reproducao

O Docker Engine estava parado; foi iniciado normalmente pelo Docker Desktop,
sem alterar sua configuracao. Utilizou-se exclusivamente balaio-develop.
Cinco imagens anteriores receberam tags locais
balaio/pi169-rollback-{servico}:20261119 antes da substituicao. Metadados de
containers/mounts e hashes dos arquivos protegidos ficaram em TEMP, sem segredos.
Foram recriados somente Profile, Gateway, Catalog, Commerce e Community.
Os demais oito containers mantiveram IDs. Os 13 containers terminaram saudaveis.

.env, certificados e chaves JWT permaneceram identicos. Os seis volumes de
balaio-local foram preservados e esse projeto permaneceu parado. Nenhum volume
foi removido e nenhum banco da aplicacao foi recriado. Testcontainers usou
somente bancos descartaveis de teste. Nao foram gerados backups com segredos.

A develop ainda nao possui os commits PI-214 de Profile/Gateway. Para evitar
misturar tickets, a validacao criou fontes temporarias com PI-214 e o diff
PI-169 e um override Compose temporario contendo somente contextos de build.
As branches originais permaneceram intactas. Para uma entrega reproduzivel,
integrar os PRs PI-214 e PI-169 pelo fluxo normal da equipe. Nao reconstruir o
Profile da develop antiga sobre o banco com V7/V8 do PI-214: essa combinacao
nao representa as fontes testadas e pode falhar na validacao Flyway.

Rollback, se necessario: retaggear cada imagem preservada para
balaio/{servico}:local e recriar somente os cinco servicos com --no-build,
--no-deps e o projeto explicito balaio-develop, preservando seus volumes.
Nenhum rollback foi necessario. Nao executar down -v ou qualquer prune.

## Pendencias e criterio de encerramento

- PI-210/205: organizador e endpoints de submissao/aprovacao; nao implementados.
- PI-177: selo/curadoria ainda sem controller no Profile; nao declarar esse
  fluxo funcional nem cria-lo apenas para comprovar autorizacao.
- PI-172 diz apenas dono, enquanto a implementacao/documentacao herdada de
  perfil admite uma excecao para admin. Ela foi preservada; confirmar com o
  responsavel antes de alterar essa decisao de negocio.
- Catalog: politica administrativa de pecas continua pendente; nenhuma permissao
  global foi concedida. O valor contratual do papel e admin, nao administrador.
- PI-207/249: varias rotas futuras do YAML nao possuem implementacao no dono;
  nao foram criadas neste ticket. POST eventos permanece pendente de PI-210.
- PI-261: consolidar OpenAPI/README/Jira; Catalog ainda sem securitySchemes;
  contratos historicos divergem quanto a cadastro privilegiado e donos de rotas.
- Papeis nos servicos consumidores refletem o JWT ate expirar. Profile confere
  o estado atual localmente; nao foi prometida revogacao imediata distribuida.

Criterios PI-169 para operacoes existentes: PASSOU para recusa de tokens no
Gateway e servicos; PASSOU para restricoes de comprador; PASSOU para operacoes
administrativas existentes; PASSOU para testes nos repositorios donos e
propriedade. Curadoria/eventos inexistentes nao sao provas aprovadas: suas
validacoes funcionais pertencem aos tickets donos. Encerramento no Jira exige
revisao da equipe do escopo existente e registro dessas dependencias, sem
apresentar funcionalidades futuras como concluidas. Nenhum status foi alterado.

## Estado final do diff (sem stage/commit)

Os stats abaixo sao de arquivos rastreados; arquivos novos sao listados a seguir
porque git diff --stat nao inclui untracked. git diff --check e
git diff --cached --check passaram nos cinco repositorios.

### api-gateway

```text
README.md                                          | 54 ++++++++++++-
 .../api_gateway/security/GatewayAccessPolicy.java  | 23 ++++--
 .../api_gateway/ApiGatewayApplicationTests.java    | 88 ++++++++++++++++++++--
 3 files changed, 146 insertions(+), 19 deletions(-)
```

Novos:

- `docs/pi-169-autorizacao.md`

### profile-service

```text
README.md                                              | 18 ++++++++++++++++++
 .../profile/account/api/AutenticacaoWebConfig.java     |  3 ++-
 .../account/api/OriginAutenticacaoInterceptor.java     |  2 +-
 .../account/api/OriginAutenticacaoInterceptorTest.java | 16 ++++++++++++++++
 .../artisan/application/PerfilArtesaoServiceTest.java  | 14 ++++++++++++++
 5 files changed, 51 insertions(+), 2 deletions(-)
```

Novos:

- `src/test/java/br/com/balaio/profile/account/api/PerfilOriginHttpTest.java`

### catalog-service

```text
README.md                                          | 32 ++++++++++++++++++++++
 .../catalog/peca/api/JwtCatalogSecurityConfig.java | 26 ++++--------------
 .../balaio/catalog/peca/api/PecasController.java   |  2 +-
 .../peca/PecasControllerIntegrationTest.java       |  3 +-
 4 files changed, 40 insertions(+), 23 deletions(-)
```

Novos:

- `src/main/java/br/com/balaio/catalog/peca/api/ValidadorClaimsJwt.java`
- `src/test/java/br/com/balaio/catalog/peca/api/JwtContractTest.java`
- `src/test/java/br/com/balaio/catalog/peca/api/JwtHttpTest.java`

### commerce-service

```text
README.md                                          | 27 ++++++++++++++++++++++
 .../security/JwtCommerceSecurityConfig.java        | 26 ++++-----------------
 2 files changed, 32 insertions(+), 21 deletions(-)
```

Novos:

- `src/main/java/br/com/balaio/commerce/security/ValidadorClaimsJwt.java`
- `src/test/java/br/com/balaio/commerce/security/JwtContractTest.java`
- `src/test/java/br/com/balaio/commerce/security/JwtHttpTest.java`

### community-service

```text
README.md                                          | 27 ++++++++++++++++++
 .../shared/security/ChavePublicaJwtLoader.java     |  5 +++-
 .../community/shared/security/SecurityConfig.java  | 32 +---------------------
 3 files changed, 32 insertions(+), 32 deletions(-)
```

Novos:

- `src/main/java/br/com/balaio/community/shared/security/ValidadorClaimsJwt.java`
- `src/test/java/br/com/balaio/community/shared/security/JwtContractTest.java`
- `src/test/java/br/com/balaio/community/shared/security/JwtHttpTest.java`
