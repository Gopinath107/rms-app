package com.ris.rms.service.impl;

import com.ris.rms.dto.SkillDto;
import com.ris.rms.entity.Skill;
import com.ris.rms.repository.SkillRepository;
import com.ris.rms.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SkillServiceImpl implements SkillService {

	private final SkillRepository repo;

	@Override
	public SkillDto create(SkillDto dto) {
		if (repo.existsBySkillNameIgnoreCase(dto.getSkillName())) {
			throw new IllegalArgumentException("Skill already exists");
		}
		Skill s = new Skill();
		s.setSkillName(dto.getSkillName());
		Skill saved = repo.save(s);
		return toDto(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public SkillDto getById(Long id) {
		Skill s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Skill not found"));
		return toDto(s);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SkillDto> list(String q, Integer page, Integer size) {
		List<Skill> base = (page != null && size != null && page >= 0 && size > 0)
				? repo.findAll(PageRequest.of(page, size)).getContent()
				: repo.findAllByOrderBySkillNameAsc();

		return base.stream()
				.filter(s -> q == null || q.isBlank() || s.getSkillName().toLowerCase().contains(q.toLowerCase()))
				.map(this::toDto).toList();
	}

	@Override
	public SkillDto update(Long id, SkillDto dto) {
		Skill existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Skill not found"));

		if (dto.getSkillName() != null && !dto.getSkillName().equalsIgnoreCase(existing.getSkillName())
				&& repo.existsBySkillNameIgnoreCase(dto.getSkillName())) {
			throw new IllegalArgumentException("Skill already exists");
		}

		if (dto.getSkillName() != null)
			existing.setSkillName(dto.getSkillName());
		Skill saved = repo.save(existing);
		return toDto(saved);
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id))
			throw new IllegalArgumentException("Skill not found");
		repo.deleteById(id);
	}

	private SkillDto toDto(Skill s) {
		SkillDto dto = new SkillDto();
		dto.setSkillId(s.getSkillId());
		dto.setSkillName(s.getSkillName());
		return dto;
	}
}
