import { describe, expect, it } from "vitest";
import { ResourceReplayService, type ReplayResourceOpener } from "../src/services/resource-replay";

describe("ResourceReplayService", () => {
  it("opens confirmed HTTP resources in input order", async () => {
    const opener = new RecordingResourceOpener();
    const service = new ResourceReplayService(opener);

    await expect(
      service.replay(
        [
          resource("one", "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html"),
          resource("two", "https://stackoverflow.com/questions/1/spring-cors")
        ],
        { confirmed: true }
      )
    ).resolves.toEqual({
      openedCount: 2,
      openedUrls: [
        "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
        "https://stackoverflow.com/questions/1/spring-cors"
      ],
      status: "opened"
    });

    expect(opener.openedUrls()).toEqual([
      "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
      "https://stackoverflow.com/questions/1/spring-cors"
    ]);
  });

  it("requires confirmation for multi-resource replay", async () => {
    const opener = new RecordingResourceOpener();
    const service = new ResourceReplayService(opener);

    await expect(
      service.replay([resource("one", "https://example.com/one"), resource("two", "https://example.com/two")], {
        confirmed: false
      })
    ).resolves.toEqual({ code: "CONFIRMATION_REQUIRED", status: "rejected" });
    expect(opener.openedUrls()).toEqual([]);
  });

  it("enforces hard cap before opening any tab", async () => {
    const opener = new RecordingResourceOpener();
    const service = new ResourceReplayService(opener);

    await expect(
      service.replay(
        [
          resource("one", "https://example.com/one"),
          resource("two", "https://example.com/two"),
          resource("three", "https://example.com/three")
        ],
        { confirmed: true, hardCap: 2 }
      )
    ).resolves.toEqual({ code: "COUNT_EXCEEDS_LIMIT", status: "rejected" });
    expect(opener.openedUrls()).toEqual([]);
  });

  it("rejects unsupported and unsafe URLs", async () => {
    const service = new ResourceReplayService(new RecordingResourceOpener());

    await expect(service.replay([resource("ftp", "ftp://example.com/file")], { confirmed: true })).resolves.toEqual({
      code: "UNSAFE_URL",
      resourceId: "ftp",
      status: "rejected"
    });
    await expect(service.replay([resource("fragment", "https://example.com/path#secret")], { confirmed: true })).resolves.toEqual({
      code: "UNSAFE_URL",
      resourceId: "fragment",
      status: "rejected"
    });
    await expect(service.replay([resource("credentials", "https://user@example.com/path")], { confirmed: true })).resolves.toEqual({
      code: "UNSAFE_URL",
      resourceId: "credentials",
      status: "rejected"
    });
  });

  it("rejects localhost and private network hosts", async () => {
    const service = new ResourceReplayService(new RecordingResourceOpener());

    const cases: Array<readonly [string, string]> = [
      ["localhost", "http://localhost:3000"],
      ["loopback", "http://127.0.0.1:8080"],
      ["private-ten", "https://10.1.2.3"],
      ["private-172", "https://172.16.0.1"],
      ["private-192", "https://192.168.1.10"],
      ["local-domain", "https://service.local/path"]
    ];
    for (const [id, url] of cases) {
      await expect(service.replay([resource(id, url)], { confirmed: true })).resolves.toEqual({
        code: "PRIVATE_HOST",
        resourceId: id,
        status: "rejected"
      });
    }
  });

  it("rejects empty selections", async () => {
    await expect(new ResourceReplayService(new RecordingResourceOpener()).replay([], { confirmed: true })).resolves.toEqual({
      code: "EMPTY_SELECTION",
      status: "rejected"
    });
  });
});

function resource(id: string, sanitizedUrl: string) {
  return { id, sanitizedUrl };
}

class RecordingResourceOpener implements ReplayResourceOpener {
  private readonly urls: string[] = [];

  open(url: string): Promise<void> {
    this.urls.push(url);
    return Promise.resolve();
  }

  openedUrls(): readonly string[] {
    return this.urls;
  }
}
