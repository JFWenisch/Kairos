function renderMermaidDiagrams() {
    if (typeof mermaid === "undefined") {
        return;
    }

    mermaid.initialize({
        startOnLoad: false,
        securityLevel: "strict",
        theme: "default"
    });

    // Convert fenced mermaid code blocks to renderable containers for Material/MkDocs.
    document.querySelectorAll("pre code.language-mermaid").forEach(function (codeBlock) {
        var pre = codeBlock.parentElement;
        if (!pre || pre.dataset.mermaidProcessed === "true") {
            return;
        }

        var container = document.createElement("div");
        container.className = "mermaid";
        container.textContent = codeBlock.textContent;
        pre.dataset.mermaidProcessed = "true";
        pre.replaceWith(container);
    });

    mermaid.run({ querySelector: ".mermaid" });
}

if (typeof window.document$ !== "undefined" && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(function () {
        renderMermaidDiagrams();
    });
} else {
    window.addEventListener("load", renderMermaidDiagrams);
}
