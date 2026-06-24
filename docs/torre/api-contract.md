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

## Admin / Cadastros (somente ADMIN)
| Método | Rota | Corpo / Params | Resposta |
|---|---|---|---|
| POST | `/api/torre/admin/usuarios` | `{login, nome, senha, idFilial, perfil}` | usuário criado (sem hash) |
| GET | `/api/torre/admin/usuarios` | `?filial` | usuários da filial |
| POST | `/api/torre/admin/usuarios/{id}/ativo` | `?ativo=true\|false` | — |
| POST | `/api/torre/admin/locais` | `{codigo, nome, tipo}` (+`?filial`) | local criado |
| GET | `/api/torre/admin/locais` | `?filial` | locais da filial (inclui inativos) |
| POST | `/api/torre/admin/locais/{id}/ativo` | `?ativo=true\|false` (+`?filial`) | — |
| POST | `/api/torre/admin/filiais` | `{idFilial, nome, dataCorteViagem, ativa}` (upsert) | filial salva |
| GET | `/api/torre/admin/filiais` | — | todas as filiais |

`tipo` de local: DOCA \| BOX \| AREA \| PENDENCIA \| AVARIA \| QUIMICA \| BOX_DEFINITIVO. Operador recebe **403**. Login/código duplicado → **409**. `GET /api/torre/locais` (operador) segue listando só os ativos.

## Auditoria (somente ADMIN)
| Método | Rota | Resposta |
|---|---|---|
| GET | `/api/torre/auditoria` | `[{id, idFilial, idUsuario, acao, entidade, idEntidade, detalhe, ocorridoEm}]` (200 mais recentes) |

Ações registradas: `FINALIZAR_ATIVIDADE`, `CANCELAR_ATIVIDADE`, `CRIAR_USUARIO`, `ATIVAR_USUARIO`/`DESATIVAR_USUARIO`, `CRIAR_LOCAL`, `ATIVAR_LOCAL`/`DESATIVAR_LOCAL`, `SALVAR_FILIAL`. Operador recebe **403** ao tentar acessar.

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
