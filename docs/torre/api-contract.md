# Torre de Controle — Contrato de API (v0)

Namespace: `/api/torre/*`. JSON via Java records. Toda chamada (exceto login) é
escopada por **filial ativa** — a do usuário autenticado; ADMIN pode trocar via
`?filial=<id>` ou header `X-Filial`.

Autenticação: Bearer JWT no header `Authorization`. O token carrega `idUsuario`,
`login`, `idFilial`, `perfil`.

## Auth
| Método | Rota | Corpo / Params | Resposta |
|---|---|---|---|
| POST | `/api/torre/auth/login` | `{login, senha}` | `{token, expiraEm, usuario:{id,nome,login,idFilial,perfil}}` |
| GET | `/api/torre/auth/me` | — | `{id,nome,login,idFilial,perfil}` |

## Sincronização / legado (leitura)
| Método | Rota | Resposta |
|---|---|---|
| GET | `/api/torre/viagens/aguardando-descarga` | `[{idViagem, tipo, placa, motorista, origem, volumes, peso, chegadaPrevista}]` (respeita `data_corte_viagem` da filial) |

## Atividade
| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| POST | `/api/torre/atividades` | `{tipo, subtipo?, idViagem?, placa?}` | atividade criada (abre + entra automático) |
| POST | `/api/torre/atividades/{id}/entrar` | `{funcao?}` | participação aberta (encerra a anterior do usuário) |
| POST | `/api/torre/atividades/{id}/sair` | — | participação encerrada |
| POST | `/api/torre/atividades/{id}/finalizar` | — | atividade finalizada + totais (tempo, horas-homem) |
| POST | `/api/torre/atividades/{id}/cancelar` | `{motivo}` (obrigatório) | atividade cancelada (encerra participantes; grava motivo + auditoria) |
| GET | `/api/torre/atividades/abertas` | — | lista de atividades abertas da filial |

## Auditoria (somente ADMIN)
| Método | Rota | Resposta |
|---|---|---|
| GET | `/api/torre/auditoria` | `[{id, idFilial, idUsuario, acao, entidade, idEntidade, detalhe, ocorridoEm}]` (200 mais recentes) |

Ações registradas hoje: `FINALIZAR_ATIVIDADE`, `CANCELAR_ATIVIDADE`. Operador recebe **403** ao tentar acessar.

## Documento
| Método | Rota | Resposta |
|---|---|---|
| GET | `/api/torre/documentos/disponiveis` | docs disponíveis na filial (armazém / box / descarga ativa) |
| POST | `/api/torre/atividades/{id}/documentos` | bipa/seleciona CT-e dentro da atividade |
| POST | `/api/torre/documentos/{id}/movimentar` | `{tipo, idLocalDestino?, idAtividadeDestino?}` (inclui cross-dock) |

## Ocorrência
| Método | Rota | Corpo / Params | Resposta |
|---|---|---|---|
| POST | `/api/torre/ocorrencias` (JSON) | `{tipo, idDocumento?, idAtividade?, placa?, descricao?}` | ocorrência criada |
| POST | `/api/torre/ocorrencias` (multipart) | parte `dados` (mesmo JSON) + parte `foto` (arquivo, opcional) | ocorrência criada c/ `fotoPath` |
| GET | `/api/torre/ocorrencias/{id}/foto` | — | binário da imagem (servidor resolve o path) |

Foto: JPG/PNG/WEBP até 10MB. O servidor gera o caminho (`<ano>/<mês>/<filial>/<uuid>.<ext>`); o cliente nunca informa path.

## Painel TV
| Método | Rota | Resposta |
|---|---|---|
| GET | `/api/torre/painel/snapshot` | snapshot com todos os blocos + `indicadores` (polling) |

Bloco `indicadores` (recorte do dia corrente, fuso do servidor):
`{atividadesFinalizadasHoje, horasHomemHojeSeg, pessoasAtivasAgora, documentosNoArmazem, ocorrenciasHoje, tempoMedioDescargaSeg}` — durações em segundos.

## Catálogos (enums)
- **TipoAtividade**: DESCARGA_TRANSFERENCIA, DESCARGA_COLETA, SEPARACAO, CARREGAMENTO, OUTRAS
- **StatusAtividade**: ABERTA, FINALIZADA, CANCELADA
- **StatusDocumento**: AGUARDANDO_DESCARGA, EM_DESCARGA, NO_ARMAZEM, EM_SEPARACAO, SEPARADO_BOX, EM_CARREGAMENTO, CARREGADO, CROSS_DOCK_DIRETO, AVARIA, DIVERGENCIA
- **PerfilCodigo**: OPERADOR, ADMIN
