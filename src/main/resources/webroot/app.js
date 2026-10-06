/**
 * Asynchronous Client for Mini Web Framework.
 * Uses Fetch API and manipulates the DOM strictly using textContent to prevent XSS.
 */
document.addEventListener("DOMContentLoaded", () => {
    const greetingForm = document.getElementById("greetingForm");
    const nameInput = document.getElementById("nameInput");
    const btnPi = document.getElementById("btnPi");
    const btnEnv = document.getElementById("btnEnv");
    const resultBox = document.getElementById("resultBox");
    const statusTag = document.getElementById("statusTag");

    async function executeFetch(url, actionLabel) {
        statusTag.textContent = actionLabel + "...";
        resultBox.textContent = "Loading " + url + "...";
        const startTime = performance.now();

        try {
            const response = await fetch(url, { method: "GET" });
            const elapsed = Math.round(performance.now() - startTime);
            const contentType = response.headers.get("content-type") || "";

            let textContent;
            if (contentType.includes("application/json")) {
                const jsonObj = await response.json();
                textContent = JSON.stringify(jsonObj, null, 2);
            } else {
                textContent = await response.text();
            }

            statusTag.textContent = `Status: ${response.status} ${response.statusText} (${elapsed}ms)`;
            // Safely set textContent to prevent XSS injection
            resultBox.textContent = `[HTTP ${response.status} ${response.statusText}] ${url}\n\n${textContent}`;

        } catch (err) {
            statusTag.textContent = "Network Error";
            // Safely set textContent
            resultBox.textContent = `Network Error: ${err.message}`;
        }
    }

    // 1. Greeting Action
    greetingForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const rawName = nameInput.value.trim();
        const url = rawName ? `/hello?name=${encodeURIComponent(rawName)}` : "/hello";
        executeFetch(url, "Fetching greeting");
    });

    // 2. Pi Action
    btnPi.addEventListener("click", () => {
        executeFetch("/pi", "Fetching Pi");
    });

    // 3. Env Action
    btnEnv.addEventListener("click", () => {
        executeFetch("/env", "Fetching cloud environment");
    });
});
