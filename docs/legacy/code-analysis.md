# Análise Técnica do Código — salome-legacy

> Gerado pelo Arqueólogo em 2026-06-08
> Escala: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA
> Foco: `salome-legacy/` — ERP legado Swing/MVC/JDBC

---

## 1. Arquitetura e Padrões

### 1.1 Padrão MVC + Data Access Layer 🟢

O legado segue um MVC-Swing com 4 camadas obrigatórias por entidade:

| Camada | Pacote | Responsabilidade |
|--------|--------|------------------|
| **View** | `view/` | Telas Swing (`.java` + `.form` Matisse) com handlers como `btnSalvarActionPerformed` |
| **Controller** | `controller/` | Delegação para Data, mas **também contém SQL direto** em controllers maiores |
| **Bean** | `model.bean/` | POJOs mutáveis com flags `*Gravar` para dirty-tracking |
| **Data** | `model.data/` | DAO com JDBC direto, SQL textual, `BancoDados.ExecutaQuery()` |
| **Table** | `model.table/` | Enums mapeando `tabela.coluna` como strings |

### 1.2 Convenção de Nomes 🟢

Cada entidade gera um conjunto de 4-5 arquivos com nome consistente:

```
XxxView.java + XxxView.form   (view/)
XxxController.java             (controller/)
XxxBean.java                   (model/bean/)
XxxData.java                   (model/data/)
XxxTable.java                  (model/table/)
```

Exceções: Views com sufixos como `Pesquisar`, `Incluir`, `Alterar` que representam variantes de tela.

---

## 2. Camada Controller — Padrões Identificados

### 2.1 Padrão CRUD Básico (90% dos controllers) 🟢

```java
public class XxxController {
    public XxxBean ler(int id)                        // READ
    public boolean salvar(XxxBean b, XxxBean old)     // UPDATE (diff-based)
    public ArrayList<XxxBean> lista()                 // LIST ALL
    public ArrayList<XxxBean> lista(XxxTable c, Object v)  // LIST BY FIELD
    public boolean incluir(XxxBean b)                 // INSERT
    public boolean excluir(int id)                    // DELETE
}
```

Cada método instancia um novo `XxxData` e delega. Sem injeção de dependência, sem singleton.

### 2.2 Padrão Update com Dirty-Tracking 🟢

O `salvar()` recebe **dois beans**: o novo e o antigo. O `Data` compara campo a campo usando as flags `*Gravar` para gerar UPDATE apenas dos campos alterados.

### 2.3 Controllers Pesados — SQL Direto no Controller 🟢

Os controllers maiores **quebram o padrão MVC** e contêm SQL direto via `Conecta.getCon().prepareStatement()`:

| Controller | Tamanho | SQL no Controller? |
|-----------|---------|-------------------|
| `ViagemController` | 115 KB / 2.164 linhas | ✅ Sim — queries de coletas, entregas, transferências, peso total |
| `TabelaPrecoController` | 95 KB | ✅ Sim — cálculos de preço |
| `FaturaController` | 65 KB | ✅ Sim — faturamento complexo |
| `NotaServicoFaturaController` | 43 KB | ✅ Sim — notas de serviço |
| `RpaController` | 31 KB | ✅ Sim — recibo de pagamento a autônomos |
| `ComprovanteentregaController` | 31 KB | ✅ Sim — comprovantes |
| `ClienteController` | 21 KB | ✅ Sim — busca por CNPJ, setor, prazo |

### 2.4 Integrações Externas nos Controllers 🟢

O `ViagemController` integra diretamente com:

- **Pamcard** (e-frete): `ConsultarFavorecido`, `InserirFavorecido`, `ConsultarCartao`, `ConsultarRNTRC`, `ConsultarFrota`, `ConsultarRemetenteDestinatario`, `InserirRemetenteDestinatario`, `AlterarRemetenteDestinatario`, `InserirContratoFrete`, `EncerrarContratoFrete`
- **E-frete**: `EfreteMotoristaUtil`, `EfreteProprietarioUtil`, `EfreteVeiculoUtil`, `EfreteUtil`
- **Ksoftlog**: `KsoftlogUtil` (rastreamento)
- **Transsat**: `TranssatUtil` (gerenciamento de risco)
- **E-mail**: `EmailUtil` com templates Velocity

### 2.5 Controle de Transação 🟢

Transações são manuais via `BancoDados.commit()` / `BancoDados.rollback()` nos controllers:

```java
if (!controller.salvar(bean, beanAntigo)) {
    BancoDados.rollback();
    return false;
}
BancoDados.commit();
```

### 2.6 Operações Assíncronas 🟢

Controllers usam `SwingWorker<String, Void>` para operações longas com diálogo `Espera`:

```java
final Espera espera = new Espera("Mensagem");
SwingWorker<String, Void> sw = new SwingWorker<>() {
    protected String doInBackground() throws Exception { ... }
    protected void done() { espera.dispose(); }
};
sw.execute();
espera.setVisible(true);
```

---

## 3. Camada Bean — Dirty-Tracking Manual

### 3.1 Padrão de Campos 🟢

Cada campo do bean tem um par: valor + flag de gravação:

```java
private String status;
private Boolean statusGravar = false;

public String getStatus() { return status; }
public void setStatus(String status) {
    this.status = status;
    this.statusGravar = true;   // marca como dirty
}
```

### 3.2 Beans Maiores (entidades complexas) 🟢

| Bean | Tamanho | Campos estimados |
|------|---------|------------------|
| `NotaFiscalSefazBean` | 56 KB | ~200+ campos (NF-e/SEFAZ) |
| `ConhecimentoBean` | 54 KB | ~200+ campos (CT-e) |
| `VeiculoBean` | 53 KB | ~200+ campos |
| `ClienteBean` | 53 KB | ~200+ campos |
| `ViagemBean` | 53 KB | ~100+ campos (viagem + MDF-e + averbação) |

### 3.3 Tipos de Dados 🟢

- `int` para IDs e quantidades
- `double` para valores monetários e pesos
- `String` para textos, status, enums como texto
- `Date` (java.util.Date) para datas
- `Timestamp` (java.sql.Timestamp) para data/hora

---

## 4. Camada Data — JDBC Direto

### 4.1 Padrão de Persistência 🟢

Dois mecanismos coexistem:

**a) SQL gerado manualmente (maioria):**
```java
String query = "SELECT col1, col2 FROM tabela WHERE ...";
PreparedStatement pstmt = Conecta.getCon().prepareStatement(query);
pstmt.setInt(1, valor);
ResultSet rs = pstmt.executeQuery();
```

**b) BancoDados.ExecutaQuery (gerado pelo framework):**
```java
return new BancoDados().ExecutaQuery(query, campos.toArray(), false);
```

### 4.2 Classe Conecta 🟡

Classe utilitária estática que mantém a conexão JDBC global:
- `Conecta.getCon()` — retorna a conexão ativa
- `Conecta.getUsuario()` — retorna o usuário logado (formato `prefixo_nome`)
- Conexão compartilhada (não pool)

### 4.3 Geração de UPDATE Dinâmico 🟢

O `salvar()` no Data monta o UPDATE dinamicamente baseado nas flags `*Gravar`:

```java
if (bean.isStatusGravar()) {
    query += ", status = ?";
    campos.add(bean.getStatus());
}
```

### 4.4 SQL Inline no Controller vs Data 🟡

- **Data**: queries padrão (CRUD simples, lista, ler, incluir, excluir, salvar)
- **Controller**: queries complexas (joins, subqueries, cálculos, agregações)

Esta é uma violação do MVC documentada mas consistente — regras de negócio com SQL complexo ficam no Controller.

---

## 5. Camada Table — Mapeamento de Colunas

### 5.1 Padrão 🟢

```java
public enum ViagemTable {
    IDVIAGEM("viagem.idViagem"),
    STATUS("viagem.status"),
    IDMOTORISTA("viagem.idMotorista"),
    ...
}
```

Cada enum value mapeia `tabela.coluna` como string. Usado em:
- `lista(XxxTable campo, Object valor)` — filtra por campo
- Construção dinâmica de WHERE clauses

### 5.2 Volume 🟢

245 arquivos Table correspondem a ~245 tabelas MySQL no banco legado.

---

## 6. Camada View — Swing + Matisse

### 6.1 Estrutura 🟢

- **395 classes Java** + **392 formulários .form** (NetBeans Matisse)
- Cada tela principal tem variantes: `Pesquisar`, `Incluir`, `Alterar`, `Visualizar`

### 6.2 Padrão de Eventos 🟡

```java
private void btnSalvarActionPerformed(ActionEvent evt) {
    XxxController controller = new XxxController();
    XxxBean bean = new XxxBean();
    bean.setCampo(txtCampo.getText());
    // ...
    if (controller.salvar(bean, beanAntigo)) {
        BancoDados.commit();
    } else {
        BancoDados.rollback();
    }
}
```

### 6.3 Padrão de Carregamento de Dados 🟡

Telas usam `JTable` com `DefaultTableModel` populado via `controller.lista()`.

---

## 7. Domínios de Negócio Identificados

### 7.1 Domínios Principais (por tamanho e complexidade) 🟢

| Domínio | Entidades Principais | Complexidade |
|---------|---------------------|--------------|
| **Viagem** | Viagem, ViagemColeta, ViagemEntrega, ViagemTransferencia, ViagemParcela, ViagemNotaServico, ViagemIntervalo | 🔴 Alta |
| **Faturamento** | Fatura, FaturaBaixa, FaturaHistorico, FaturaRetorno, FaturaParceiro | 🔴 Alta |
| **Conhecimento (CT-e)** | Conhecimento, ConhecimentoNotasFiscais, ConhecimentoDocumentos, ConhecimentoProdutos | 🔴 Alta |
| **Nota Fiscal (NF-e)** | NotaFiscal, NotaFiscalProdutos, NotaFiscalDuplicatas, NotaFiscalSefaz, NotaFiscalCupom | 🔴 Alta |
| **Nota Serviço** | NotaServico, NotaServicoFatura, NotaServicoCartaCorrecao | 🟡 Média |
| **Cliente** | Cliente, ClienteContato, ClienteAtividade, ClientePrazo, ClienteRegiao, ClienteRegiaoSetor | 🟡 Média |
| **Veículo** | Veiculo, VeiculoHodometro, VeiculoPneus, VeiculoRevisao, VeiculoSeguro, VeiculoIpva, VeiculoLicenciamento | 🟡 Média |
| **Coleta** | Coleta, ColetaProdutos, ColetaStatusHistorico | 🟡 Média |
| **RPA** | Rpa, RpaConfiguracao, RpaImpostoRenda, RpaInss, RpaValePedagio | 🟡 Média |
| **Comprovante Entrega** | ComprovanteEntrega, ComprovanteEntregaImagem, ComprovanteEntregaNotaServico | 🟡 Média |
| **Tabela de Preço** | TabelaPreco, TabelaPrecoCidades, TabelaPrecoDestinatarios, TabelaPrecoRemetentes | 🟡 Média |
| **Ordem de Serviço** | OrdemServico, OrdemServicoApontamento, OrdemServicoPeca, OrdemServicoProprio, OrdemServicoTerceiro | 🟡 Média |
| **Fornecedor** | Fornecedor, FornecedorGrupo, FornecedorProdutos, FornecedorCompraProdutoGrupo | 🟢 Baixa |
| **Motorista** | Motorista | 🟢 Baixa |
| **Proprietário** | Proprietario | 🟢 Baixa |
| **Pneu** | Pneu, PneuMarca, PneuRecapeamento, PneusRessolagem, ConsertoPneu | 🟢 Baixa |
| **Gerenciamento de Risco** | GerenciamentoRisco, GerenciamentoRiscoVeiculo | 🟢 Baixa |
| **Cotação** | Cotacao, CotacaoCubagens | 🟢 Baixa |
| **Cadastros Auxiliares** | Cidade, Estado, Pais, Banco, Cbo, Cfop, Cnae, Feriado, Segmento, Setor, Linha, Rota, Rodovia | 🟢 Baixa |

### 7.2 Integrações Externas 🟢

| Integração | Módulos | Pacote/Classe |
|-----------|---------|---------------|
| **Pamcard (e-frete/CIOT)** | Viagem | `br.com.salome.pamcard.*` |
| **E-frete (ANTT)** | Viagem | `br.com.salome.util.Efrete*Util` |
| **Ksoftlog (rastreamento)** | Viagem | `br.com.salome.util.KsoftlogUtil` |
| **Transsat (GR)** | Viagem, GerenciamentoRisco | `br.com.salome.util.TranssatUtil` |
| **SEFAZ (NF-e/CT-e/MDF-e)** | NotaFiscal, Conhecimento, Viagem | `NotaFiscalSefazController`, `CteSpedController` |
| **E-mail (SMTP)** | Cliente, Viagem | `br.com.salome.util.EmailUtil` + Velocity |
| **Jasper Reports** | Vários | 🟡 Inferido via deploy/relatorios |

---

## 8. Regras de Negócio Críticas

### 8.1 Cancelamento de Viagem 🟢

Fluxo em `ViagemController.cancelaViagem()`:
1. Status da viagem → "Cancelada"
2. Todos CT-e de entrega → situação "Armazém"
3. Todos CT-e de transferência → situação "Armazém"
4. Todas coletas → status "Pendente"
5. `BancoDados.commit()` no final (transação manual)

### 8.2 Contrato de Frete (Pamcard) 🟢

Fluxo em `ViagemController.inserirContratoFrete()` (SwingWorker):
1. Valida dados obrigatórios (CNPJ, endereço, CEP < 8 chars, etc.)
2. Consulta/Insere Favorecido (motorista)
3. Consulta/Insere Favorecido (proprietário do veículo)
4. Consulta Cartão do motorista
5. Consulta RNTRC motorista e proprietário
6. Consulta Frota
7. Para cada CT-e: consulta/insere/altera Remetente/Destinatário
8. Insere contrato de frete na Pamcard

### 8.3 Validação de Cliente para Pamcard 🟢

Em `ViagemController.validaClientePamcard()`:
- CNPJ/CPF obrigatório
- Razão Social obrigatória
- Endereço obrigatório
- Número obrigatório (max 5 chars)
- Bairro obrigatório
- Código IBGE da cidade obrigatório
- CEP obrigatório (max 8 chars)

### 8.4 Cálculo de Peso Total da Viagem 🟢

`ViagemController.getPesoTotal()`: soma `pesoNf` de:
- coletas (`viagemcoletas → coleta`)
- entregas (`viagementrega → conhecimentonotasfiscais`)
- transferências (`viagemtransferencia → viagemtransferenciaconhecimento → conhecimentonotasfiscais`)

### 8.5 Prazo de Entrega 🟢

`ClienteController.getHorasSetor()`:
- Busca prazo pelo CEP do endereço de entrega
- Fallback para CEP do cadastro do cliente
- Lookup via `clienteregiaosetorlocal.prazo`

### 8.6 E-mail de Cadastro Pendente 🟢

`ClienteController.enviarEmailClientePendente()`:
- Apenas clientes com `situacaoCadastro = "Pendente"` e sem data/hora de envio
- Template Velocity com dados do cliente
- Envio para `filial.emailPendenciasCadastro`
- Flag `emailPendenciasCadastroEnviado = "Sim"` após envio

---

## 9. Observações Técnicas

### 9.1 Problemas de Encoding 🟢

Strings com caracteres acentuados aparecem corrompidos no código-fonte (encoding ISO-8859-1 ou CP1252):
- `"Armaz�m"` (Armazém)
- `"N�o foi poss�vel"` (Não foi possível)
- `"Ve�culo"` (Veículo)

### 9.2 SQL Injection 🟢

Alguns controllers usam concatenação direta de strings em SQL:
```java
String query = "SELECT ... WHERE cnpj_cpf = '" + cnpj + "'";  // VULNERÁVEL
String query = "SELECT ... WHERE idCliente = " + idCliente;    // VULNERÁVEL
```

Outros usam `PreparedStatement` corretamente.

### 9.3 Resource Leaks 🟢

Padrão inconsistente de fechamento de `ResultSet` e `PreparedStatement`:
- Alguns fecham `rs.close(); pstmt.close();`
- Alguns não fecham
- Nenhum usa try-with-resources (Java 7+)

### 9.4 Conexão Global 🟢

`Conecta.getCon()` retorna uma conexão única compartilhada. Auto-commit desabilitado — commit/rollback manual.
