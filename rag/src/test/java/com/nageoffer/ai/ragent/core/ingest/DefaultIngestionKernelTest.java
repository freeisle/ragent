/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.core.ingest;

import com.nageoffer.ai.ragent.core.chunk.ChunkingService;
import com.nageoffer.ai.ragent.core.chunk.model.Chunk;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkAssembler;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkDraft;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkMetadata;
import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.embed.ChunkEmbeddingService;
import com.nageoffer.ai.ragent.core.ingest.sink.ChunkIndexWriter;
import com.nageoffer.ai.ragent.core.ingest.summary.DocumentSummarizer;
import com.nageoffer.ai.ragent.core.parser.DocumentParser;
import com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.core.parser.registry.ParserRegistry;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultIngestionKernel}：文档摘要块前置与普通块重编号
 */
class DefaultIngestionKernelTest {

    private static final DocumentRef DOC = new DocumentRef("doc-1", "kb-1", "设计文档.txt");
    private static final VectorTarget TARGET = new VectorTarget("kb-1", "embed-model", 3);

    private final ParserRegistry parserRegistry = mock(ParserRegistry.class);
    private final DocumentParser parser = mock(DocumentParser.class);
    private final ChunkingService chunkingService = mock(ChunkingService.class);
    private final ChunkEmbeddingService chunkEmbeddingService = mock(ChunkEmbeddingService.class);
    private final ChunkIndexWriter chunkIndexWriter = mock(ChunkIndexWriter.class);
    private final DocumentSummarizer documentSummarizer = mock(DocumentSummarizer.class);

    private final DefaultIngestionKernel kernel = new DefaultIngestionKernel(
            parserRegistry, chunkingService, chunkEmbeddingService, chunkIndexWriter, documentSummarizer);

    @BeforeEach
    void setUp() {
        when(parserRegistry.require(any(), any())).thenReturn(parser);
        when(parser.parseStructured(any(), any(), any()))
                .thenReturn(ParsedDocument.of(List.of(new ParagraphBlock(Provenance.ofFile(DOC.filename()), "正文"))));
        when(chunkingService.chunk(any(), any())).thenReturn(regularChunks(2));
        when(chunkEmbeddingService.embed(anyList(), any()))
                .thenAnswer(invocation -> ((List<Chunk>) invocation.getArgument(0)).stream()
                        .map(c -> new EmbeddedChunk(c, new float[]{1f, 2f, 3f}))
                        .toList());
    }

    private static List<Chunk> regularChunks(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> ChunkAssembler.assemble(i, ChunkDraft.of("第" + i + "块内容", ChunkMetadata.empty())))
                .toList();
    }

    private static Chunk summaryChunk() {
        return ChunkAssembler.assemble(0, ChunkDraft.of("## 文档摘要\n\n摘要正文", "摘要正文", ChunkMetadata.empty()));
    }

    private IngestionOutcome run() {
        return kernel.run(DOC, "hello world".getBytes(), IngestionSpec.defaults(), TARGET);
    }

    @Test
    @DisplayName("摘要存在时前置为 index 0，普通块整体后移并随 replaceDocument 落库")
    void prependsSummaryAndRenumbers_whenSummaryPresent() {
        when(documentSummarizer.summarize(any(), any())).thenReturn(Optional.of(summaryChunk()));

        IngestionOutcome outcome = run();

        List<Chunk> chunks = outcome.chunks();
        assertEquals(3, chunks.size());
        assertEquals("## 文档摘要\n\n摘要正文", chunks.get(0).content(), "摘要块应排在首位");
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index(), "普通块整体后移");
        assertEquals(2, chunks.get(2).index());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmbeddedChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkIndexWriter).replaceDocument(any(), any(), captor.capture());
        List<EmbeddedChunk> written = captor.getValue();
        assertEquals(3, written.size());
        assertEquals("## 文档摘要\n\n摘要正文", written.get(0).content());
        assertEquals(1, written.get(1).chunk().index());
    }

    @Test
    @DisplayName("摘要缺失时序号原样从 0 起，不改动任何块")
    void keepsOriginalIndices_whenSummaryAbsent() {
        when(documentSummarizer.summarize(any(), any())).thenReturn(Optional.empty());

        IngestionOutcome outcome = run();

        List<Chunk> chunks = outcome.chunks();
        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index());
    }

    @Test
    @DisplayName("普通分块为空时仍抛异常：摘要不是正文替代品")
    void throws_whenChunkingYieldsEmpty() {
        when(documentSummarizer.summarize(any(), any())).thenReturn(Optional.of(summaryChunk()));
        when(chunkingService.chunk(any(), any())).thenReturn(List.of());

        ClientException e = assertThrows(ClientException.class, this::run);
        assertTrue(e.getMessage().contains("分块结果为空"));
    }
}
