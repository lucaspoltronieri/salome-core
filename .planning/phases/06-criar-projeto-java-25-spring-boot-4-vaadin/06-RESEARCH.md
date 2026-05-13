# Phase 6: Criar projeto Java 25 + Spring Boot 4 + Vaadin - Research

**Researched:** 2026-05-13
**Domain:** bootstrap Java/Spring/Vaadin com leitura segura do MySQL legado
**Confidence:** HIGH

<user_constraints>
## User Constraints from CONTEXT.md

### Locked Decisions
- **D-01..D-04:** `salome-core` deve nascer como Maven modulo unico, com pacotes raiz `ui`, `application`, `domain`, `infrastructure`, `security`, subpacotes `contaspagar` e um `shared` pequeno.
- **D-05..D-09:** a aplicacao abre direto em **Gestao de Pagamentos**, com casca Vaadin completa/interativa, menu lateral, placeholders clicaveis e vocabulario de produto moderno.
- **D-10..D-16:** a fase conecta ao MySQL legado em leitura ativa, bloqueia escrita funcional, usa profiles/variaveis externas, instala Flyway sem gerenciar o legado e cria adapters/repositories orientados ao dominio.
- **D-17..D-20:** Spring Security fica preparado, sem login real obrigatorio; usuario tecnico fixo de desenvolvimento; papeis basicos preparados; contrato do adapter de usuario legado criado.

### Hard Guardrails
- Nao alterar `salome-legacy`.
- Nao alterar banco de producao.
- Nao executar migrations no MySQL legado.
- Nao colocar SQL nem regra financeira pesada em View Vaadin.
- Toda leitura real deve passar por Service e Repository/Adapter.
</user_constraints>

<research_summary>
## Summary

Phase 6 should create a runnable Spring Boot/Vaadin project, not a full finance migration. The best implementation shape is a small but real vertical foundation: Maven and dependency management first, package/layer skeleton second, read-only legacy integration third, then a Vaadin application shell that opens directly on `Gestao de Pagamentos`.

Because the user explicitly asked for data connected to the legacy database, the plan should include a real MySQL datasource and a domain-oriented `LegacyGestaoPagamentosRepository`. Because the roadmap still requires read-first safety, the plan must keep writes blocked by design and avoid migration execution against the legacy database.

The UI can be visually close to `references/ux-frontend/screens/contas-pagar.jsx`, but should be implemented as native Vaadin Java components, not React copy/paste. The first data slice should be the smallest useful read model for the initial screen: title list, selected title details, product/detail area placeholder backed by available read data, and bottom `Central de Pagamentos` shell disabled/read-only.
</research_summary>

<official_docs_findings>
## Official Documentation Findings

### Spring Boot 4 and Java 25
- Spring Boot 4.0.6 requires Java 17+ and is compatible up to Java 26, so Java 25 is within the documented compatibility range.
- Spring Boot 4 declares Maven 3.6.3+ as supported.
- Source: https://docs.spring.io/spring-boot/system-requirements.html

### Vaadin with Spring Boot
- Vaadin's Spring Boot integration uses `vaadin-spring-boot-starter`.
- Vaadin recommends managing Vaadin dependencies with the `vaadin-bom`.
- Production frontend builds should use `vaadin-maven-plugin` with `prepare-frontend` and `build-frontend`.
- Vaadin views are Java classes detected with `@Route`.
- Source: https://vaadin.com/docs/latest/flow/integrations/spring/spring-boot

### Maven compiler
- Maven Compiler Plugin defaults are still source/target 8 unless configured.
- The plugin documentation recommends setting the `release` option explicitly.
- For this project, Maven should set `maven.compiler.release=25`.
- Source: https://maven.apache.org/plugins/maven-compiler-plugin/index.html

### Flyway and Spring Boot
- Spring Boot exposes `spring.flyway.enabled`, `spring.flyway.clean-disabled`, `spring.flyway.locations`, `spring.flyway.url`, `spring.flyway.user` and related properties.
- `spring.flyway.clean-disabled` defaults to true, which aligns with safety, but the plan should still set/verify safe behavior explicitly.
- Since Flyway defaults to using the primary datasource when no Flyway URL is set, this phase must disable Flyway execution against the legacy datasource unless/until there is a separate managed database.
- Source: https://docs.spring.io/spring-boot/appendix/application-properties/
</official_docs_findings>

<implementation_guidance>
## Implementation Guidance

### Maven bootstrap
- Create `pom.xml` at repository root with packaging `jar`.
- Use Spring Boot 4 parent or dependency management.
- Set `java.version` and `maven.compiler.release` to `25`.
- Add dependencies for Vaadin, Spring Boot, Spring JDBC, Spring Security, MySQL Connector/J, Flyway, validation and tests.
- Use Vaadin BOM and Vaadin Maven Plugin.

### Package structure
- Use base package `br.com.salome.core`.
- Create real package roots:
  - `ui`
  - `application`
  - `domain`
  - `infrastructure`
  - `security`
  - `shared`
- Prepare `contaspagar` below the relevant layers, even if the user-facing label is `Gestao de Pagamentos`.

### Legacy datasource
- Configure datasource via profiles and environment variables such as:
  - `SALOME_LEGACY_DB_URL`
  - `SALOME_LEGACY_DB_USERNAME`
  - `SALOME_LEGACY_DB_PASSWORD`
- Use a dedicated configuration class for the legacy datasource and `JdbcTemplate`.
- Mark all initial service/repository flows as read-only.
- Do not create insert/update/delete methods in phase 6 repositories.

### Flyway safety
- Include Flyway dependency and migration folder structure only for future governance.
- Set Flyway disabled for the active legacy datasource profiles.
- Add documentation/config comments that Flyway must not point at the legacy MySQL datasource.
- Do not create a migration against the legacy database in this phase.

### UI shell
- Implement native Vaadin with `AppLayout`, menu side navigation and routes.
- Root route should open `Gestao de Pagamentos`.
- Future menu items should be clickable placeholders: `Documento de Entrada`, `Painel Financeiro`, `Fluxo de Caixa`, `Movimento Financeiro`.
- `Gestao de Pagamentos` should have top filters/actions, left title list, right detail/product area and bottom `Central de Pagamentos` area.
- Mutating buttons should be visually present only if needed, but disabled or clearly non-operational in phase 6.

### Security foundation
- Configure Spring Security to allow local access without real login for phase 6.
- Provide a `CurrentUserContext` abstraction.
- Provide a fixed development user implementation.
- Define role/authority constants or enum-like types for future mapping.
- Define a `LegacyUserAdapter` contract without implementing real legacy authentication.
</implementation_guidance>

<risks>
## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Spring Boot auto-runs Flyway against the legacy datasource | Could alter or add metadata to the legacy DB | Disable Flyway by default for legacy profiles and do not set Flyway URL to legacy DB. |
| App cannot compile/run without real DB credentials | Slows local verification | Keep repository integration injectable and tests focused on services/read models; only live DB read requires env vars. |
| Vaadin View receives SQL or `JdbcTemplate` directly | Recreates legacy coupling | Plan must enforce View -> Service -> Repository/Adapter. |
| UI scope drifts into full Phase 7 | Larger, riskier foundation | Deliver shell plus smallest real read slice only; defer full validation to Phase 7/8. |
| "Gestao de Pagamentos" name diverges from legacy maps | Traceability confusion | Use user-facing label `Gestao de Pagamentos`, but keep package/domain mapping `contaspagar` and cite legacy origins. |
| Login real is attempted too early | Blocks phase on unknown legacy password strategy | Use fixed dev user and adapter contract only. |
</risks>

<recommended_plan_shape>
## Recommended Plan Shape

One executable plan is enough for Phase 6, because this is a single bootstrap slice with tightly coupled setup tasks. The task order should be:

1. Create Maven/Spring/Vaadin project skeleton.
2. Add layered package structure and configuration profiles.
3. Add read-only legacy datasource, repository contract and first read model.
4. Add application service for `Gestao de Pagamentos`.
5. Add Spring Security foundation with fixed dev user and legacy user adapter contract.
6. Add Vaadin shell, side menu, placeholders and `Gestao de Pagamentos` layout.
7. Add tests and verification commands.
8. Document local run/env setup and Flyway legacy safety.
</recommended_plan_shape>

<sources>
## Sources

### Official
- Spring Boot System Requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Vaadin Spring Boot integration: https://vaadin.com/docs/latest/flow/integrations/spring/spring-boot
- Maven Compiler Plugin: https://maven.apache.org/plugins/maven-compiler-plugin/index.html
- Spring Boot common application properties, Flyway section: https://docs.spring.io/spring-boot/appendix/application-properties/

### Project
- `AGENTS.md`
- `.planning/phases/06-criar-projeto-java-25-spring-boot-4-vaadin/06-CONTEXT.md`
- `docs/architecture/salome-core-architecture.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/codebase/CONTAS-PAGAR-MAPA-BANCO-QUERIES.md`
- `.planning/codebase/CONTAS-PAGAR-CLASSES-MAPA-TECNICO.md`
- `.planning/codebase/USUARIO-ACESSO-MAPA.md`
- `references/ux-frontend/screens/contas-pagar.jsx`
</sources>

<metadata>
## Metadata

**Research date:** 2026-05-13
**Valid until:** 2026-06-12
**Ready for planning:** yes
</metadata>

---

*Phase: 06-Criar projeto Java 25 + Spring Boot 4 + Vaadin*
*Research completed: 2026-05-13*
