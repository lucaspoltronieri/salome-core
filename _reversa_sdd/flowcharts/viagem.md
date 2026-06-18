# Fluxogramas — Módulo Viagem

> Gerado pelo Arqueólogo em 2026-06-08
> Foco: `salome-legacy/` — `ViagemController.java`

## Fluxo de Cancelamento de Viagem (`cancelaViagem`)

Este fluxo descreve as etapas sequenciais e transacionais do cancelamento de uma viagem.

```mermaid
flowchart TD
    Start([Início: cancelaViagem]) --> LerViagem[Ler ViagemBean do BD]
    LerViagem --> |Sucesso| AlterarStatus[Setar Status = Cancelada]
    LerViagem --> |Falha| EndErro([Falso: Viagem não encontrada])
    
    AlterarStatus --> SalvarViagem[Salvar Viagem]
    SalvarViagem --> |Falha| Rollback[BancoDados.rollback]
    SalvarViagem --> |Sucesso| ListaEntregas[Listar CT-es de Entrega da Viagem]
    
    ListaEntregas --> LoopEntregas{Para cada CT-e}
    LoopEntregas --> |Sim| SetArmazemE[Setar Situação = Armazém]
    SetArmazemE --> SalvarCteE[Salvar CT-e]
    SalvarCteE --> |Falha| Rollback
    SalvarCteE --> |Sucesso| LoopEntregas
    
    LoopEntregas --> |Não| ListaTransf[Listar Transferências da Viagem]
    ListaTransf --> LoopTransf{Para cada Transferência}
    LoopTransf --> |Sim| ListaCteT[Listar CT-es da Transferência]
    ListaCteT --> LoopCteT{Para cada CT-e}
    LoopCteT --> |Sim| SetArmazemT[Setar Situação = Armazém]
    SetArmazemT --> SalvarCteT[Salvar CT-e]
    SalvarCteT --> |Falha| Rollback
    SalvarCteT --> |Sucesso| LoopCteT
    LoopCteT --> |Não| LoopTransf
    
    LoopTransf --> |Não| ListaColetas[Listar Coletas da Viagem]
    ListaColetas --> LoopColetas{Para cada Coleta}
    LoopColetas --> |Sim| SetPendente[Setar Status = Pendente]
    SetPendente --> SalvarColeta[Salvar Coleta]
    SalvarColeta --> |Falha| Rollback
    SalvarColeta --> |Sucesso| LoopColetas
    
    LoopColetas --> |Não| Commit[BancoDados.commit]
    Commit --> EndSucesso([Verdadeiro: Cancelamento Concluído])
    Rollback --> EndErroRollback([Falso: Erro com Rollback])
```

## Fluxo de Inserção de Contrato de Frete Pamcard (`inserirContratoFrete`)

Executado de forma assíncrona (`SwingWorker`) para integrar a viagem com o serviço CIOT Pamcard.

```mermaid
flowchart TD
    Start([Início: Inserir Contrato de Frete]) --> ReadBeans[Ler Beans: Motorista, Veículo, Proprietário]
    ReadBeans --> ConsultarFavorecidoMot[Pamcard: Consultar Favorecido - Motorista]
    ConsultarFavorecidoMot --> |Não Existe| InserirFavorecidoMot[Pamcard: Inserir Favorecido - Motorista]
    ConsultarFavorecidoMot --> |Existe| ConsultarFavorecidoProp
    InserirFavorecidoMot --> ConsultarFavorecidoProp[Pamcard: Consultar Favorecido - Proprietário]
    
    ConsultarFavorecidoProp --> |Não Existe| InserirFavorecidoProp[Pamcard: Inserir Favorecido - Proprietário]
    ConsultarFavorecidoProp --> |Existe| ConsultarCartao
    InserirFavorecidoProp --> ConsultarCartao[Pamcard: Consultar Cartão Motorista]
    
    ConsultarCartao --> ConsultarRNTRC_Mot[Pamcard: Consultar RNTRC Motorista]
    ConsultarRNTRC_Mot --> ConsultarRNTRC_Prop[Pamcard: Consultar RNTRC Proprietário]
    ConsultarRNTRC_Prop --> ConsultarFrota[Pamcard: Consultar Frota]
    
    ConsultarFrota --> ListCtes[Listar todos os CT-es da Viagem]
    ListCtes --> LoopCtes{Para cada CT-e}
    
    LoopCtes --> |Sim| CteEmitente[Pamcard: Consultar Emitente]
    CteEmitente --> |Não Existe| InsCteEmitente[Pamcard: Inserir Emitente]
    CteEmitente --> |Existe| AltCteEmitente[Pamcard: Alterar Emitente]
    InsCteEmitente --> LoopCtes
    AltCteEmitente --> LoopCtes
    
    LoopCtes --> |Não| FimValidacoes[Validações Prontas]
    FimValidacoes --> InserirContrato[Pamcard: Inserir Contrato Frete]
    InserirContrato --> End([Fim])
    
    %% Tratamento de erro simplificado
    InserirFavorecidoMot -.-> |Erro| ErrorEnd([Fim com Erro])
    ConsultarCartao -.-> |Erro| ErrorEnd
    InserirContrato -.-> |Erro| ErrorEnd
```
