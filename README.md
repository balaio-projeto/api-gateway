# Balaio API Gateway

Entrada HTTP do frontend para os cinco microsserviços. O gateway preserva caminho, método, query e corpo, valida a sessão e encaminha uma credencial Bearer confiável. Cabeçalhos de identidade enviados pelo cliente são removidos. Cada serviço implementa seus contratos, revalida a identidade e controla seus próprios dados e permissões.

## Destinos configurados

| Serviço | URL local padrão | Caminhos da Fake API encaminhados |
| --- | --- | --- |
| catalog-service | http://localhost:8081 | Peças, histórico e relacionadas, peças do artesão, referências e arquivos de fotos |
| profile-service | http://localhost:8082 | Auth, usuário, conta, artesãos, coletivos, selo, curadoria e histórico de busca |
| commerce-service | http://localhost:8083 | Checkout, fretes, pedidos, pedidos pendentes, vendas e sacola |
| community-service | http://localhost:8084 | Perguntas, conversas, avaliações, vídeos, mediações, favoritos e estado dos vídeos |
| events-service | http://localhost:8085 | Eventos |

Todas as rotas conservam o prefixo `/api/v1`. Os cinco destinos e 45 padrões de caminho foram preservados. O catálogo implementa `GET /api/v1/pecas`; o profile implementa cadastro/login/alias/logout. Os outros encaminhamentos preparam a borda: configurar uma rota não implementa o endpoint no serviço de destino.

Os caminhos compartilhados são específicos para evitar conflito: `/pecas/{slug}/perguntas` vai para comunicação e as demais rotas de peça vão para catálogo; `/pedidos/{id}/conversa` e `/pedidos/{id}/avaliacao` vão para comunicação e as rotas transacionais de pedido vão para comércio. `GET /api/v1/estado` ainda precisa de um contrato de composição entre sacola, favoritos e histórico antes de ser encaminhado; as rotas `PUT /estado/...` já apontam para os respectivos donos.

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

## Segurança — etapa 4

O gateway usa filtros Servlet, compatíveis com **Spring Cloud Gateway Server WebMVC**. Não utiliza filtros WebFlux nem consulta o profile a cada chamada. A configuração obrigatória de segurança deve existir mesmo quando os serviços opcionais estiverem parados.

| Variável | Configuração |
| --- | --- |
| `JWT_PUBLIC_KEY_LOCATION` | Caminho ou URI `file:` de arquivo externo com pública RSA X.509 PEM, mínimo 3072 bits |
| `JWT_ISSUER` | Mesmo emissor configurado no profile |
| `JWT_AUDIENCE` | Mesma audiência configurada no profile |
| `AUTH_ALLOWED_ORIGINS` | Lista separada por vírgulas de origens explícitas do frontend, por exemplo `http://localhost:4321` |

Ausência ou configuração inválida impede o startup, com erro sanitizado. Não são aceitos chave privada, chave inline, URL remota, recurso classpath ou compartilhamento UNC. O gateway **não recebe nem gera chave privada**. Exporte a pública do par usado pelo profile e disponibilize somente esse arquivo ao gateway, com mount de leitura. Nunca monte o arquivo privado no container/processo do gateway.

O decoder usa chave local fixa e aceita somente **RS256**. Verifica assinatura e os claims no JSON assinado, sem relaxar os tipos por coerção: `sub` UUID canônico, `papel` em `comprador/artesao/organizador/admin`, emissor, audiência textual ou lista de textos contendo a audiência configurada, `iat`/`exp` inteiros e `jti` textual não vazio. Rejeita claims duplicados, algoritmo inesperado e headers que tentem fornecer outra chave. `exp` deve estar no futuro, depois de `iat`, e o intervalo não pode superar 3600 segundos. `iat` pode antecipar o relógio local em no máximo 30 segundos; isso não prolonga a expiração. `nbf`, se presente, deve ser inteiro e não pode estar no futuro.

`organizador` é reconhecido como valor reservado do contrato JWT; o profile ainda não o provisiona (PI-210). Um token desse papel não recebe permissões de artesão ou administrador.

### Cookie, Bearer e encaminhamento

- Aceita cookie `balaio_sessao` ou header `Authorization: Bearer <JWT>`.
- Se ambos estiverem presentes, ambos são validados. Tokens idênticos são aceitos; tokens diferentes mas válidos retornam 400. Qualquer token inválido retorna 401, sem fallback para outra identidade.
- Headers Authorization duplicados ou cookies de sessão duplicados retornam 400 nas rotas normais. Um cookie vazio ou esquema/token inválido retorna 401.
- Depois da validação, encaminha **um** `Authorization: Bearer <JWT>` e remove o cookie de autenticação. Preserva outros cookies, incluindo `balaio_cliente`, e respostas `Set-Cookie` dos serviços.
- Remove headers de identidade controlados pelo cliente, incluindo famílias `X-User-*`, `X-Account-*`, `X-Role*`, `X-Auth*`, `X-Papel`, `X-Identity*` e equivalentes. `Forwarded`/`X-Forwarded-*` recebidos também são removidos; não substituem identidade nem origem confiável. A fonte de autenticação é o JWT validado.

Os POSTs exatos de `/auth/cadastro`, `/auth/login`, `/auth/entrar`, `/auth/recuperacao` e `/auth/logout` são anônimos. Neles, credenciais anteriores são ignoradas e removidas antes do encaminhamento, inclusive tokens inválidos/expirados e cookies legados duplicados; isso permite recuperar o login e limpar a sessão. O controle Origin/cookie ocorre **antes** dessa remoção. Não há liberação genérica de `/auth/**`.

Nas demais rotas públicas, credenciais são opcionais, mas, quando fornecidas, precisam ser válidas. A borda não encaminha uma credencial inválida apenas porque a leitura seria pública.

### CORS e CSRF

Origem é comparada exatamente por esquema, host e porta; não aceita wildcard, `null`, múltiplos valores ou origem fora da lista. `localhost` e `127.0.0.1` são origens distintas. Origem presente não autorizada retorna 403 mesmo para Bearer.

Métodos permitidos no CORS: GET, HEAD, POST, PUT, PATCH, DELETE e OPTIONS. Headers permitidos: Authorization, Content-Type e Accept. Credenciais só são habilitadas para uma origem explicitamente permitida. Preflight válido recebe 204 sem exigir JWT nem alcançar os serviços; origem, método ou header proibido retorna 403 no envelope da API.

Em métodos mutáveis autenticados por cookie, Origin ausente também retorna 403. Acrescentar Bearer junto do cookie não dispensa a proteção. Cliente API com Bearer exclusivo pode operar sem Origin. Endpoints anônimos de sessão preservam a mesma barreira se houver cookie anterior.

`Vary: Origin` é mantido inclusive sem Origin. Headers CORS permissivos enviados pelo upstream não podem sobrescrever a política da borda; `Set-Cookie` e demais headers de negócio continuam preservados. Os filtros de geração de Forwarded/X-Forwarded do Gateway estão desabilitados e `server.forward-headers-strategy=none`.

### Matriz de acesso e limites

As regras dependem de **método e caminho**, sem alterar os destinos do YAML:

- Leituras documentadas de catálogo, perguntas da peça, artesãos, coletivos, referências, imagens, fretes, eventos, vídeos e comentários são públicas. HEAD acompanha GET. Não há wildcard de leitura que abra rotas privadas de painel/configuração.
- Conta, endereços, pedidos, checkout, conversas e avaliações exigem autenticação. A propriedade do recurso deve ser verificada no serviço.
- Painel `/artesao/*`, configurações do ateliê, criação/edição/exclusão de peças, respostas a perguntas e transições artesanais de pedido exigem artesão. PATCH de perfil de artesão admite artesão ou admin, conforme PI-171; isso não dispensa a verificação de posse.
- Administração exige admin, **exceto POST exato `/admin/mediacoes`**, que exige autenticação e pertence ao fluxo de abertura de mediação do comprador. GET/PATCH da administração continuam restritos.
- POST de eventos, vídeos e comentários, além dos PUTs de estado de visitante, permanecem públicos conforme o contrato atual. PI-169/PI-210 devem fechar a evolução dessas permissões; esta etapa não inventa um novo requisito de organizador.
- GET `/videos?painel=true` exige artesão porque inclui rascunhos no painel; a regra verifica todos os valores do parâmetro para evitar ambiguidade. Esta proteção é uma inferência explícita do uso pela UI, não uma transformação de toda leitura de vídeos em privada.
- Caminhos ambíguos na API, como percent-encoding no caminho, matrix parameters, barras duplicadas e traversal, são recusados com 400. Query params continuam preservados. Rotas novas não herdam acesso público automaticamente.

`GET /estado` continua sem destino/composição. A listagem GET `/admin/mediacoes` é administrativa no contrato; o formulário de comprador que hoje a chama precisará de alinhamento no frontend, sem abrir uma listagem global pela borda.

Rejeições do gateway usam `{dados:null,erro:{codigo,mensagem}}`: **401 / NAO_AUTENTICADO**, **403 / SEM_PERMISSAO** ou **400 / REQUISICAO_INVALIDA** para credenciais/caminhos ambíguos. Respostas de negócio dos serviços são preservadas. Health/info permanecem locais e públicos.

Gateway validado não substitui revalidação e autorização nos serviços. A etapa 5 ainda precisa proteger acessos diretos às portas publicadas dos microsserviços, verificar dono do recurso e tratar papel persistido atualizado. O JWT é um snapshot de até 60 minutos; não há revogação imediata no gateway. Frontend, Fake API e Compose não foram migrados nesta etapa. [Arquitetura de comunicação](https://projeto-integrador.atlassian.net/wiki/spaces/DDS/pages/26148865), [contratos](https://projeto-integrador.atlassian.net/wiki/spaces/DDS/pages/28999681).

## Verificação

Com JDK 25 no `JAVA_HOME`, execute `.\mvnw.cmd -B -ntp clean verify`. Os testes geram RSA em execução, gravam somente a pública temporária para o gateway e usam cinco upstreams HTTP locais controlados. Não dependem de Docker, banco ou serviços de negócio reais. A chave privada do emissor de teste fica em memória, sem fixture privada versionada.

O teste cobre 45 exemplos de roteamento, JWT, Cookie/Bearer, falsificação de identidade, CORS/preflight, CSRF, política de papéis e configuração obrigatória. Isso comprova comportamento da borda e o encaminhamento HTTP; não comprova ownership ou revalidação nos serviços ainda pendentes.
