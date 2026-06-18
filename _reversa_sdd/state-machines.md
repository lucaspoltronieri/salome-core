# Máquinas de Estado — salome-legacy

> Gerado pelo Detetive em 2026-06-08
> Escala: 🟢 CONFIRMADO | 🟡 INFERIDO | 🔴 LACUNA

## 1. Viagem

A tabela `viagem` controla os veículos e cargas em trânsito. O campo central é o `status` (String literal no banco).

```mermaid
stateDiagram-v2
    [*] --> Emitida : Criação
    Emitida --> Em_Viagem : Iniciar viagem
    Emitida --> Cancelada : Cancelar antes de iniciar
    
    Em_Viagem --> Encerrada : Concluir rota (encerraViagem)
    
    %% O cancelamento pode ocorrer em trânsito dependendo das regras fiscais
    Em_Viagem --> Cancelada : (Excepcional) Cancelamento logístico
    
    Cancelada --> [*]
    Encerrada --> [*]
```

> **Nota:** Quando a viagem passa para `Cancelada`, o controlador cascateia a alteração para os Conhecimentos e Coletas, forçando `Armazém` e `Pendente` respectivamente. 🟢

---

## 2. Conhecimento de Transporte (CT-e)

Representa o conhecimento fiscal. Acompanha a carga fisicamente. O campo é `situacao`.

```mermaid
stateDiagram-v2
    [*] --> Aberta : Lançamento via XML/Manual
    
    Aberta --> Armazém : Disponível para roteirizar
    Aberta --> Inutilizada : Cancelamento na SEFAZ (antes do uso)
    
    Armazém --> Em_Viagem : Inserida numa Viagem Ativa
    
    Em_Viagem --> Armazém : Viagem Cancelada ou Devolvida ao Pátio
    Em_Viagem --> Finalizada : Comprovante de Entrega lançado
    Em_Viagem --> Entregue : Baixa manual de entrega
    
    Finalizada --> [*]
    Entregue --> [*]
    Inutilizada --> [*]
```

> **Nota:** Conhecimentos nascem "Abertos" para manipulação, ficam em "Armazém" aguardando viagem, "Em Viagem" quando alocados, e finalizam com a entrega. 🟡

---

## 3. Coleta

Ordem para o veículo de rua ir buscar a mercadoria no cliente. O campo é `status`.

```mermaid
stateDiagram-v2
    [*] --> Pendente : Criada
    
    Pendente --> Em_Viagem : Alocada numa Viagem
    Pendente --> Cancelada : Cliente desistiu ou erro
    
    Em_Viagem --> Pendente : Viagem cancelada (devolve à fila)
    Em_Viagem --> Realizada : Mercadoria recebida no pátio / RecebimentoColeta
    
    Realizada --> [*]
    Cancelada --> [*]
```

> **Nota:** Em `RecebimentoColeta.java:1189`, o status vira "REALIZADA" e um CT-e (Conhecimento) com status "ABERTA" é gerado a partir dela. 🟢

---

## 4. Ordem de Serviço (Oficina)

Ordem de manutenção de veículos próprios ou terceiros. O campo é `situacao`.

```mermaid
stateDiagram-v2
    [*] --> Aberta : Criada
    
    Aberta --> Encerrada : Peças e serviços apontados (fechamento)
    Aberta --> Cancelada : Serviço não executado
    
    Encerrada --> Aberta : (Excepcional) Reabertura por supervisor
    
    Encerrada --> [*]
    Cancelada --> [*]
```
