package com.sistema_restful.oficina_mecanica.service;

import com.sistema_restful.oficina_mecanica.dto.OrcamentoDTO;
import com.sistema_restful.oficina_mecanica.dto.OrcamentoResponseDTO;
import com.sistema_restful.oficina_mecanica.exception.ResourceNotFoundException;
import com.sistema_restful.oficina_mecanica.model.*;
import com.sistema_restful.oficina_mecanica.repository.ClienteRepository;
import com.sistema_restful.oficina_mecanica.repository.OrcamentoRepository;
import com.sistema_restful.oficina_mecanica.repository.PecaRepository;
import com.sistema_restful.oficina_mecanica.repository.ServicoRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrcamentoService {

    @Autowired
    private OrcamentoRepository orcamentoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ServicoRepository servicoRepository;

    @Autowired
    private PecaRepository pecaRepository;

    @Transactional
    public OrcamentoResponseDTO salvarOrcamento(OrcamentoDTO orcamentoDTO) {
        // Validação do cliente
        Cliente cliente = clienteRepository.findById(orcamentoDTO.getClienteId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado com ID: " + orcamentoDTO.getClienteId()));

        // Criação do orçamento
        Orcamento orcamento = new Orcamento();
        orcamento.setCliente(cliente);
        orcamento.setDataCriacao(LocalDate.now());

        // Processar serviços
        List<OrcamentoServico> orcamentoServicos = orcamentoDTO.getServicos().stream().map(servicoDTO -> {
            Servico servico = servicoRepository.findById(servicoDTO.getServicoId())
                    .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado com ID: " + servicoDTO.getServicoId()));

            if (servicoDTO.getQuantidade() <= 0) {
                throw new IllegalArgumentException("Quantidade do serviço deve ser maior que zero: ID " + servicoDTO.getServicoId());
            }

            OrcamentoServico orcamentoServico = new OrcamentoServico();
            orcamentoServico.setServico(servico);
            orcamentoServico.setQuantidade(servicoDTO.getQuantidade());
            orcamentoServico.setOrcamento(orcamento);

            return orcamentoServico;
        }).collect(Collectors.toList());

        // Processar peças
        List<OrcamentoPeca> orcamentoPecas = orcamentoDTO.getPecas().stream().map(pecaDTO -> {
            Peca peca = pecaRepository.findById(pecaDTO.getPecaId())
                    .orElseThrow(() -> new IllegalArgumentException("Peça não encontrada com ID: " + pecaDTO.getPecaId()));

            if (pecaDTO.getQuantidade() <= 0) {
                throw new IllegalArgumentException("Quantidade da peça deve ser maior que zero: ID " + pecaDTO.getPecaId());
            }

            OrcamentoPeca orcamentoPeca = new OrcamentoPeca();
            orcamentoPeca.setPeca(peca);
            orcamentoPeca.setQuantidade(pecaDTO.getQuantidade());
            orcamentoPeca.setOrcamento(orcamento);

            return orcamentoPeca;
        }).collect(Collectors.toList());

        // Associar serviços e peças ao orçamento
        orcamento.setOrcamentoServicos(orcamentoServicos);
        orcamento.setOrcamentoPecas(orcamentoPecas);

        // Calcular valores do orçamento
        orcamento.calcularValorTotal();

        // Salvar orçamento no repositório
        Orcamento orcamentoSalvo = orcamentoRepository.save(orcamento);

        // Converter para DTO de resposta
        return toResponseDTO(orcamentoSalvo);
    }

    @Transactional(readOnly = true)
    public List<OrcamentoResponseDTO> listarOrcamentos() {
        return orcamentoRepository.findAll().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrcamentoResponseDTO> listarOrcamentosEspecificos(List<Long> ids) {
        List<Orcamento> orcamentos = orcamentoRepository.findAllById(ids);

        if (orcamentos.isEmpty()) {
            throw new IllegalArgumentException("Nenhum orçamento encontrado para os IDs fornecidos.");
        }

        return orcamentos.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    private OrcamentoResponseDTO toResponseDTO(Orcamento orcamento) {
        OrcamentoResponseDTO response = new OrcamentoResponseDTO();
        response.setId(orcamento.getId());
        response.setClienteId(orcamento.getCliente().getId());
        response.setDataCriacao(orcamento.getDataCriacao());
        response.setValorTotal(orcamento.getValorTotal());
        response.setValorDesconto(orcamento.getValorDesconto());

        response.setServicos(orcamento.getOrcamentoServicos().stream()
                .map(os -> new OrcamentoDTO.ServicoQuantidadeDTO(os.getServico().getId(), os.getQuantidade()))
                .collect(Collectors.toList()));

        response.setPecas(orcamento.getOrcamentoPecas().stream()
                .map(op -> new OrcamentoDTO.PecaQuantidadeDTO(op.getPeca().getId(), op.getQuantidade()))
                .collect(Collectors.toList()));

        return response;
    }


    public Orcamento atualizarOrcamento(Long id, OrcamentoDTO orcamentoDTO) {
        Orcamento orcamentoExistente = orcamentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento com ID " + id + " não encontrado."));

        Cliente cliente = clienteRepository.findById(orcamentoDTO.getClienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente com ID " + orcamentoDTO.getClienteId() + " não encontrado."));
        orcamentoExistente.setCliente(cliente);

        List<OrcamentoServico> orcamentoServicos = mapearOrcamentoServicos(orcamentoDTO.getServicos(), orcamentoExistente);
        List<OrcamentoPeca> orcamentoPecas = mapearOrcamentoPecas(orcamentoDTO.getPecas(), orcamentoExistente);

        orcamentoExistente.setOrcamentoServicos(orcamentoServicos);
        orcamentoExistente.setOrcamentoPecas(orcamentoPecas);

        orcamentoExistente.calcularValorTotal();
        return orcamentoRepository.save(orcamentoExistente);
    }

    public void deletarOrcamento(Long id) {
        if (!orcamentoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Orçamento com ID " + id + " não encontrado.");
        }
        orcamentoRepository.deleteById(id);
    }

    private List<OrcamentoServico> mapearOrcamentoServicos(List<OrcamentoDTO.ServicoQuantidadeDTO> servicos, Orcamento orcamento) {
        return servicos.stream()
                .map(dto -> {
                    Servico servico = servicoRepository.findById(dto.getServicoId())
                            .orElseThrow(() -> new ResourceNotFoundException("Serviço com ID " + dto.getServicoId() + " não encontrado."));
                    OrcamentoServico orcamentoServico = new OrcamentoServico();
                    orcamentoServico.setServico(servico);
                    orcamentoServico.setQuantidade(dto.getQuantidade());
                    orcamentoServico.setOrcamento(orcamento);
                    return orcamentoServico;
                })
                .collect(Collectors.toList());
    }

    private List<OrcamentoPeca> mapearOrcamentoPecas(List<OrcamentoDTO.PecaQuantidadeDTO> pecas, Orcamento orcamento) {
        return pecas.stream()
                .map(dto -> {
                    Peca peca = pecaRepository.findById(dto.getPecaId())
                            .orElseThrow(() -> new ResourceNotFoundException("Peça com ID " + dto.getPecaId() + " não encontrada."));
                    OrcamentoPeca orcamentoPeca = new OrcamentoPeca();
                    orcamentoPeca.setPeca(peca);
                    orcamentoPeca.setQuantidade(dto.getQuantidade());
                    orcamentoPeca.setOrcamento(orcamento);
                    return orcamentoPeca;
                })
                .collect(Collectors.toList());
    }
}
