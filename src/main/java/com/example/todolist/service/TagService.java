package com.example.todolist.service;

import com.example.todolist.dto.tag.TagRequest;
import com.example.todolist.dto.tag.TagResponse;
import com.example.todolist.entity.Tag;
import com.example.todolist.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> getAll() {
        return tagRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TagResponse getById(Long id) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tag not found"));
        return toResponse(tag);
    }

    @Transactional
    public TagResponse create(TagRequest request) {
        if (tagRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException("Tag already exists");
        }
        Tag tag = Tag.builder().name(request.getName()).color(request.getColor()).build();
        return toResponse(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse update(Long id, TagRequest request) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tag not found"));
        tag.setName(request.getName());
        tag.setColor(request.getColor());
        return toResponse(tagRepository.save(tag));
    }

    @Transactional
    public void delete(Long id) {
        tagRepository.deleteById(id);
    }

    private TagResponse toResponse(Tag tag) {
        return TagResponse.builder().id(tag.getId()).name(tag.getName()).color(tag.getColor()).build();
    }
}
