package br.com.salome.core.application.financeiro;

import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoDia;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoFiltro;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoLancamento;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoResumo;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoSnapshot;
import br.com.salome.core.domain.financeiro.FluxoCaixaPrevistoStatus;
import br.com.salome.core.domain.notacompra.LegacyOrigin;
import br.com.salome.core.security.CurrentUser;
import br.com.salome.core.security.CurrentUserContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FluxoCaixaPrevistoService {

    private static final LegacyOrigin ORIGIN = LegacyOrigin.of(
            "salome-legacy/view/NotaCompraDuplicatas.java",
            "formWindowOpened / EmitirCheques / Extrato.getSaldo",
            "fluxo caixa previsto aggregate query",
            "notacompraduplicatas + notacompra + extrato + banco + filial + fornecedor + planocontascentrocusto + planocontas + v_saldobancariotalao"
    );

    private final FluxoCaixaPrevistoRepository repository;
    private final CurrentUserContext currentUserContext;

    public FluxoCaixaPrevistoService(FluxoCaixaPrevistoRepository repository) {
        this(repository, null);
    }

    @Autowired
    public FluxoCaixaPrevistoService(FluxoCaixaPrevistoRepository repository, CurrentUserContext currentUserContext) {
        this.repository = repository;
        this.currentUserContext = currentUserContext;
    }

    @Transactional(readOnly = true)
    public FluxoCaixaPrevistoSnapshot consultar(FluxoCaixaPrevistoFiltro filtro) {
        FluxoCaixaPrevistoFiltro filtroEfetivo = aplicarContextoUsuario(filtro == null ? FluxoCaixaPrevistoFiltro.padrao() : filtro);
        validarPeriodo(filtroEfetivo);

        BigDecimal saldoInicial = zero(repository.consultarSaldoInicial(filtroEfetivo));
        List<FluxoCaixaPrevistoLancamento> lancamentos = repository.listarLancamentos(filtroEfetivo).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FluxoCaixaPrevistoLancamento::dataPrevista,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FluxoCaixaPrevistoLancamento::dataRealizada,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FluxoCaixaPrevistoLancamento::idNotaCompra,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FluxoCaixaPrevistoLancamento::idNotaCompraDuplicata,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<FluxoCaixaPrevistoDia> timeline = construirTimeline(filtroEfetivo, saldoInicial, lancamentos);
        FluxoCaixaPrevistoResumo resumo = construirResumo(saldoInicial, timeline, lancamentos);
        return new FluxoCaixaPrevistoSnapshot(filtroEfetivo, resumo, timeline, lancamentos);
    }

    private FluxoCaixaPrevistoFiltro aplicarContextoUsuario(FluxoCaixaPrevistoFiltro filtro) {
        if (currentUserContext == null) {
            return filtro;
        }
        CurrentUser usuario = currentUserContext.currentUser();
        FluxoCaixaPrevistoFiltro efetivo = filtro;
        if (efetivo.filial() == null && usuario.filialId() != null) {
            efetivo = efetivo.comFilial(String.valueOf(usuario.filialId()));
        }
        if (efetivo.banco() == null && usuario.bancoCaixaId() != null) {
            efetivo = efetivo.comBanco(String.valueOf(usuario.bancoCaixaId()));
        }
        return efetivo;
    }

    private void validarPeriodo(FluxoCaixaPrevistoFiltro filtro) {
        if (!filtro.hasPeriodoValido()) {
            throw new IllegalArgumentException("O periodo final nao pode ser anterior ao inicial.");
        }
    }

    private List<FluxoCaixaPrevistoDia> construirTimeline(FluxoCaixaPrevistoFiltro filtro, BigDecimal saldoInicial,
            List<FluxoCaixaPrevistoLancamento> lancamentos) {
        LocalDate inicio = filtro.periodoInicio();
        LocalDate fim = filtro.periodoOperacionalFim();
        Map<LocalDate, List<FluxoCaixaPrevistoLancamento>> agrupado = new LinkedHashMap<>();
        for (FluxoCaixaPrevistoLancamento lancamento : lancamentos) {
            LocalDate data = dataEfetiva(lancamento);
            if (data == null || data.isBefore(inicio) || data.isAfter(fim)) {
                continue;
            }
            agrupado.computeIfAbsent(data, ignored -> new ArrayList<>()).add(lancamento);
        }

        List<FluxoCaixaPrevistoDia> timeline = new ArrayList<>();
        BigDecimal saldoProjetado = saldoInicial;
        BigDecimal saldoRealizado = saldoInicial;
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            List<FluxoCaixaPrevistoLancamento> itensDoDia = agrupado.getOrDefault(dia, List.of());
            BigDecimal entradasPrevistas = soma(itensDoDia, item -> !item.realizado() && isEntrada(item), FluxoCaixaPrevistoLancamento::valorProjetadoEfetivo);
            BigDecimal entradasRealizadas = soma(itensDoDia, item -> item.realizado() && isEntrada(item), FluxoCaixaPrevistoLancamento::valorRealizadoEfetivo);
            BigDecimal saidasPrevistas = soma(itensDoDia, item -> !item.realizado() && !isEntrada(item), FluxoCaixaPrevistoLancamento::valorProjetadoEfetivo);
            BigDecimal saidasRealizadas = soma(itensDoDia, item -> item.realizado() && !isEntrada(item), FluxoCaixaPrevistoLancamento::valorRealizadoEfetivo);

            saldoProjetado = saldoProjetado.add(entradasPrevistas).add(entradasRealizadas)
                    .subtract(saidasPrevistas).subtract(saidasRealizadas);
            saldoRealizado = saldoRealizado.add(entradasRealizadas)
                    .subtract(saidasRealizadas);

            timeline.add(new FluxoCaixaPrevistoDia(
                    dia,
                    timeline.isEmpty() ? saldoInicial : timeline.get(timeline.size() - 1).saldoProjetado(),
                    entradasPrevistas,
                    entradasRealizadas,
                    saidasPrevistas,
                    saidasRealizadas,
                    saldoProjetado,
                    saldoRealizado,
                    count(itensDoDia, item -> !item.realizado()),
                    count(itensDoDia, FluxoCaixaPrevistoLancamento::realizado),
                    List.copyOf(itensDoDia),
                    ORIGIN
            ));
        }
        return List.copyOf(timeline);
    }

    private FluxoCaixaPrevistoResumo construirResumo(BigDecimal saldoInicial, List<FluxoCaixaPrevistoDia> timeline,
            List<FluxoCaixaPrevistoLancamento> lancamentos) {
        FluxoCaixaPrevistoDia ultimaLinha = timeline.isEmpty() ? null : timeline.get(timeline.size() - 1);
        BigDecimal saldoFinalProjetado = ultimaLinha == null ? saldoInicial : ultimaLinha.saldoProjetado();
        BigDecimal saldoFinalRealizado = ultimaLinha == null ? saldoInicial : ultimaLinha.saldoRealizado();
        BigDecimal totalEntradasPrevistas = soma(timeline, FluxoCaixaPrevistoDia::entradasPrevistas);
        BigDecimal totalEntradasRealizadas = soma(timeline, FluxoCaixaPrevistoDia::entradasRealizadas);
        BigDecimal totalSaidasPrevistas = soma(timeline, FluxoCaixaPrevistoDia::saidasPrevistas);
        BigDecimal totalSaidasRealizadas = soma(timeline, FluxoCaixaPrevistoDia::saidasRealizadas);
        long quantidadePrevistos = lancamentos.stream().filter(item -> !item.realizado()).count();
        long quantidadeRealizados = lancamentos.stream().filter(FluxoCaixaPrevistoLancamento::realizado).count();

        return new FluxoCaixaPrevistoResumo(
                saldoInicial,
                saldoFinalProjetado,
                saldoFinalRealizado,
                totalEntradasPrevistas,
                totalEntradasRealizadas,
                totalSaidasPrevistas,
                totalSaidasRealizadas,
                lancamentos.size(),
                quantidadePrevistos,
                quantidadeRealizados,
                ORIGIN
        );
    }

    private LocalDate dataEfetiva(FluxoCaixaPrevistoLancamento lancamento) {
        if (lancamento.realizado() && lancamento.dataRealizada() != null) {
            return lancamento.dataRealizada();
        }
        return lancamento.dataPrevista();
    }

    private boolean isEntrada(FluxoCaixaPrevistoLancamento lancamento) {
        return lancamento.valorProjetadoEfetivo().signum() < 0 || lancamento.valorRealizadoEfetivo().signum() < 0;
    }

    private BigDecimal soma(List<FluxoCaixaPrevistoDia> days,
            java.util.function.Function<FluxoCaixaPrevistoDia, BigDecimal> extractor) {
        return days.stream()
                .map(extractor)
                .map(this::zero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal soma(List<FluxoCaixaPrevistoLancamento> items,
            java.util.function.Predicate<FluxoCaixaPrevistoLancamento> predicate,
            java.util.function.Function<FluxoCaixaPrevistoLancamento, BigDecimal> extractor) {
        return items.stream()
                .filter(predicate)
                .map(extractor)
                .map(this::zero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long count(List<FluxoCaixaPrevistoLancamento> items,
            java.util.function.Predicate<FluxoCaixaPrevistoLancamento> predicate) {
        return items.stream().filter(predicate).count();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
