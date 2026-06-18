# Dicionário de Dados — salome-legacy

> Gerado pelo Arqueólogo em 2026-06-08
> Escala: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA
> Fonte: `model/table/` (245 enums) + `model/bean/` (250 beans) + `model/data/` (239 DAOs)

---

## Entidades Principais

> Cada entidade corresponde a uma tabela MySQL. As colunas foram extraídas dos enums em `model/table/` e
> dos beans em `model/bean/`. Os tipos foram inferidos dos getters/setters nos beans.

### viagem 🟢

Tabela central do módulo de viagens. ~100+ colunas.

| Coluna | Tipo Java | Obrigatório | Descrição |
|--------|-----------|-------------|-----------|
| idViagem | int | PK | ID da viagem |
| data | Date | 🟡 | Data de criação |
| hora | String | 🟡 | Hora de criação |
| status | String | 🟢 | Status: "Cancelada", "Encerrada", "Em Viagem", etc. |
| manutencao | String | 🟡 | Flag de manutenção |
| dataInicio / horaInicio | Date / String | 🟡 | Início real |
| dataFim / horaFim | Date / String | 🟡 | Fim real |
| dataPrevistaInicio / horaPrevistaInicio | Date / String | 🟡 | Previsão de início |
| dataPrevistaFim / horaPrevistaFim | Date / String | 🟡 | Previsão de fim |
| idVeiculo | int | FK | Veículo principal |
| idVeiculoCarreta | int | FK | Carreta (se houver) |
| idMotorista | int | FK | Motorista |
| idFornecedorAjudante1 / 2 | int | FK | Ajudantes |
| hodometroInicial / Final | double | 🟡 | Km |
| responsavelInicio / Fim | String | 🟡 | Quem liberou/encerrou |
| idLinhas | int | FK | Linha de rota |
| idFilial | int | FK | Filial |
| qtPalete / qtKit / qtCarrinhoMao / qtCarrinhoHidraulico | int | 🟡 | Quantidades de equipamentos |
| kitEpc | String | 🟡 | EPC do kit |
| observacao | String | 🟡 | Observações |
| valeFrete | String | 🟡 | Vale frete |
| freteTotal | double | 🟡 | Valor total do frete |
| pedagio | double | 🟡 | Valor pedágio |
| adiantamento | double | 🟡 | Valor adiantamento |
| estadia | double | 🟡 | Valor estadia |
| mdfeCiot* | String | 🟡 | Campos CIOT (emissão, cancelamento, protocolo, estado) |
| contratoFreteViagem* | String | 🟡 | Campos contrato de frete Pamcard |
| mdfe* | String | 🟡 | Campos MDF-e (série, número, chave, recibo, protocolo, status) |
| sm* | String | 🟡 | Campos SM (solicitação de monitoramento/rastreamento) |
| averbacao* | String | 🟡 | Campos de averbação (status, protocolo, data, hora) |
| ksoftlog* | String | 🟡 | Campos Ksoftlog (rastreamento) |

---

### cliente 🟢

| Coluna | Tipo Java | Obrigatório | Descrição |
|--------|-----------|-------------|-----------|
| idCliente | int | PK | ID |
| razaoSocial | String | 🟢 | Razão social |
| cnpj_cpf | String | 🟢 | CNPJ ou CPF |
| endereco | String | 🟢 | Endereço |
| numero | String | 🟢 | Número (max 5 chars para Pamcard) |
| bairro | String | 🟢 | Bairro |
| cep | String | 🟢 | CEP (max 8 chars) |
| idCidade | int | FK | Cidade |
| situacaoCadastro | String | 🟡 | "Pendente", "Ativo", etc. |
| pendenciasCadastro | String | 🟡 | Descrição das pendências |
| usuarioCadastro | String | 🟡 | Quem cadastrou |
| dataCadastro / horaCadastro | Date / String | 🟡 | Data/hora cadastro |
| dataCadastroPendente / horaCadastroPendente | Date / String | 🟡 | Data/hora envio pendência |
| emailPendenciasCadastroEnviado | String | 🟡 | "Sim" / null |
| diasEntrega | int | 🟡 | Prazo de entrega padrão |

---

### conhecimento 🟢

CT-e (Conhecimento de Transporte Eletrônico). ~200+ colunas.

| Coluna | Tipo Java | Obrigatório | Descrição |
|--------|-----------|-------------|-----------|
| idConhecimento | int | PK | ID |
| cte | String | 🟡 | Número do CT-e |
| situacao | String | 🟢 | "Armazém", "Em Viagem", "Entregue", etc. |
| idClienteEmitente | int | FK | Emitente |
| idClienteDestinatario | int | FK | Destinatário |
| idClienteConsignatario | int | FK | Consignatário |
| idClienteRedespacho | int | FK | Redespacho (sobrescreve destinatário quando preenchido) |

---

### conhecimentonotasfiscais 🟢

| Coluna | Tipo Java | Obrigatório | Descrição |
|--------|-----------|-------------|-----------|
| idConhecimentoNotasFiscais | int | PK | ID |
| idConhecimento | int | FK | CT-e |
| pesoNf | double | 🟡 | Peso da NF (usado no cálculo de peso total da viagem) |

---

### coleta 🟢

| Coluna | Tipo Java | Obrigatório | Descrição |
|--------|-----------|-------------|-----------|
| idColeta | int | PK | ID |
| idClienteRemetente | int | FK | Remetente |
| remetenteEndereco | String | 🟡 | Endereço de coleta |
| remetenteCep | String | 🟡 | CEP do remetente |
| remetenteIdCidade | int | FK | Cidade do remetente |
| status | String | 🟢 | "Pendente", "Coletada", etc. |
| pesoNf | double | 🟡 | Peso da NF |

---

### Tabelas de Relacionamento (viagem) 🟢

| Tabela | PKs | Relação |
|--------|-----|---------|
| viagemcoletas | idViagem + idColeta | Viagem ↔ Coleta (N:N) |
| viagementrega | idViagem + idConhecimento | Viagem ↔ CT-e de Entrega (N:N) |
| viagemtransferencia | idViagemTransferencia, idViagem, idFilialOrigem, idFilialDestino | Viagem → Transferência entre filiais |
| viagemtransferenciaconhecimento | idViagemTransferencia + idConhecimento | Transferência ↔ CT-e (N:N) |
| viagemnotaservico | idViagem + idNotaServico | Viagem ↔ Nota de Serviço (N:N) |
| viagemparcela | idViagem | Parcelas de pagamento da viagem |
| viagemintervalo | idViagem | Intervalos/paradas da viagem |

---

### Tabelas Auxiliares (cadastros) 🟢

| Tabela | Descrição |
|--------|-----------|
| cidade | Cidades com idEstado e codigoIBGE |
| estado | Estados com UF |
| pais | Países |
| filial | Filiais da empresa |
| empresa | Dados da empresa (e-mail, config) |
| fornecedor | Fornecedores (vinculado a motorista e proprietário) |
| motorista | Motoristas com cartaoNumero (Pamcard) |
| proprietario | Proprietários de veículos com idFornecedor |
| veiculo | Veículos com categoria, idProprietario |
| endereco | Endereços com tipo ("Coleta", "Entrega") por cliente |
| clienteregiaosetorlocal | CEP → setor com prazo de entrega |
| clienteregiaosetor | Setor com descrição |
| clienteregiao | Região |
| tabelapreco | Tabela de preço com prazoPrevistoEntrega |
| notaservico | Notas de serviço |
| rpa | Recibo de Pagamento a Autônomos |

---

## Observações

1. **~245 tabelas** mapeadas via enums em `model/table/`
2. **Sem DDL ou migrations** — o schema só existe no banco de produção 🔴
3. **Sem constraints explícitas** no código — FKs são implícitas via joins no SQL
4. **Valores de status são strings literais** — sem enum de banco, validação apenas no código
5. **Datas e horas separadas** — padrão `Date data` + `String hora` em vez de `Timestamp`
