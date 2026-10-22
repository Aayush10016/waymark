import { describe, expect, it, vi } from "vitest";

import { VsCodeCodeContextController } from "../src/code-context/vscode-code-context";

import type { OutputChannel } from "vscode";
import type { CodeContextPackageBuilder } from "../src/code-context/code-context-package-builder";
import type { CodeContextPackage, SourceSelection } from "../src/code-context/models";

const state = vi.hoisted(() => ({selectedText:"first selection"}));
vi.mock("vscode", () => ({
  window: {
    activeTextEditor: {
      selection:{isEmpty:false,start:{line:0},end:{line:0}},
      document:{uri:{fsPath:"/workspace/example.ts"},languageId:"typescript",getText:()=>state.selectedText,offsetAt:()=>0}
    },
    showInformationMessage:vi.fn()
  },
  workspace:{getWorkspaceFolder:()=>({name:"example",uri:{fsPath:"/workspace"}})},
  commands:{executeCommand:async()=>Promise.resolve([])}
}));

describe("Show Code Context", () => {
  it("rebuilds context when the selected code changes", async () => {
    const build = vi.fn(async (selection:SourceSelection) => {
      await Promise.resolve();
      return {codeContext:selection} as unknown as CodeContextPackage;
    });
    const appendLine=vi.fn();
    const output={clear:vi.fn(),appendLine,show:vi.fn()} as unknown as OutputChannel;
    const controller=new VsCodeCodeContextController({build} as unknown as CodeContextPackageBuilder,output);
    await controller.showCodeContext(context=>context.codeContext.selectedText);
    state.selectedText="second selection";
    await controller.showCodeContext(context=>context.codeContext.selectedText);
    expect(build).toHaveBeenCalledTimes(2);
    expect(appendLine).toHaveBeenNthCalledWith(1,"first selection");
    expect(appendLine).toHaveBeenNthCalledWith(2,"second selection");
  });
});
