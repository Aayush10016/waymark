import { createRoot } from "react-dom/client";
import { RecallSearchPanel, RuntimeSidePanelRecallClient } from "../sidepanel/recall";
import type { RecallSearchResultPayloadEnvelope } from "@trail/extension-messages";

const recallClient = new RuntimeSidePanelRecallClient();

async function handleSearch(query: string | null): Promise<RecallSearchResultPayloadEnvelope> {
  const result = await recallClient.search(query);

  if (result.status === "received") {
    return result.payload;
  }

  return {
    degradationMode: null,
    query,
    results: [],
    status: "unavailable"
  };
}

createRoot(document.getElementById("root")!).render(
  <main
    aria-label="Trail app"
    style={{
      background: "#07080A",
      minHeight: "100vh",
      padding: "24px"
    }}
  >
    <RecallSearchPanel
      onOpenResult={async (result) => {
        await recallClient.openSolvedProblem(result);
      }}
      onSearch={handleSearch}
      surface="full-page"
    />
  </main>
);
