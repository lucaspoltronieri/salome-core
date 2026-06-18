# Matriz de Permissões (RBAC) — salome-legacy

> Gerado pelo Detetive em 2026-06-08
> Escala: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Padrão de Autorização

O sistema legado usa um modelo híbrido de autorização fortemente acoplado às classes Swing (Views). Não existe uma biblioteca de segurança (como Spring Security ou Shiro) interceptando chamadas. A segurança é resolvida nas próprias telas ou em utilitários locais.

As abordagens identificadas são:

### 1.1 Controle por "Nível" (Hierarquia linear) 🟢
Alguns controles baseiam-se em um campo numérico de `Nível` no usuário. O sistema habilita ou desabilita componentes da interface dependendo do nível do usuário logado.
```java
usuario.getNivel() > 5 // Exemplo de inferência de hierarquia
```

### 1.2 Controle de Visibilidade de Telas (Roles) 🟡
Menus (`JMenu`, `JMenuItem`) chamam `setVisible(true/false)` ou `setEnabled(true/false)` durante a inicialização (`initComponents` ou contrutores da tela principal) com base nas permissões retornadas do banco.

### 1.3 Verificação Imperativa em Ações Críticas 🟢
Para operações sensíveis, a ação dispara um método validador que trava a execução.
Exemplo: `verificaPermissaoAlteraCaixa(Conecta.getUsuario(), idCentroCusto)` antes de permitir alterar um fechamento financeiro no `PagamentoCaixa`.

### 1.4 Controle de Filial (Data-level Security) 🟡
O método `Conecta.getUsuario()` frequentemente está atrelado ao registro do usuário que contém a propriedade `idFilial`. Várias queries JDBC usam o `idFilial` do usuário logado para injetar cláusulas `WHERE filial.id = ?`, garantindo isolamento de dados por filial.

---

## 2. Tipos de Acesso Explícitos

A matriz abaixo é reconstruída a partir da interface e campos auxiliares encontrados.

| Ação / Domínio | Papel/Condição de Acesso | Confiança |
|----------------|--------------------------|-----------|
| **Caixa Financeiro** | Permissão explícita via `verificaPermissaoAlteraCaixa()` para contas além do caixa de filial. | 🟢 |
| **Aprovação de Cotações** | Supervisor ou Gerente (`Nível` superior). | 🟡 |
| **Configuração de CTe** | Usuário restrito via configuração de campo `Clientes Perm.Conhecimento` em parâmetros do sistema. | 🟢 |
| **Cancelamento Retroativo** | (CT-e / Viagem) Bloqueado para usuários comuns após emissão na SEFAZ. | 🟡 |
| **Visualização de Dados** | Restrito aos dados pertencentes à mesma `idFilial` do usuário, exceto matriz. | 🟡 |

> 🔴 **Lacuna:** O modelo exato de tabelas de permissões (ex: `usuario_permissao`, `roles`) não pôde ser completamente mapeado sem as schemas originais, mas a presença de roles foi detectada nas checagens (`txtNivel`, `getPapelOperacao`).
