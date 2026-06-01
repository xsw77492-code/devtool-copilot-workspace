package com.devtoolcopilot.docgen.service;

import com.devtoolcopilot.docgen.dto.DocGenRequest;
import com.devtoolcopilot.docgen.dto.DocGenResponse;

public interface DocGenService {
    DocGenResponse generatePptx(Long userId, DocGenRequest req);

    DocGenResponse generateDocx(Long userId, DocGenRequest req);
}

