import { describe, expect, it } from "vitest";

import { aliasesFor, extractRulePatterns } from "../src/guardian/rule-patterns";
import { ruleAppliesToGuardianTarget } from "../src/guardian/scope";

import type { ExplicitRule } from "../src/project-source/rules";

function rule(ruleText:string,scope="project-wide"):ExplicitRule {
  return {id:"qa",title:"Constraint",status:"active",type:"technology",rawStatus:"active",rawType:"technology",scope,ruleText,reason:"QA",source:"QA",path:"/workspace/.waymark/rules/qa.md",isActive:true,hasMetadataWarning:false};
}

describe("Guardian supported rule syntax", () => {
  it.each([
    "Redis must not be used.","Redis is not allowed.","Do not use Redis.","Don't use Redis.",
    "Must not add Redis.","Never introduce Redis.","Forbid Redis.","Forbidden use of Redis.",
    "Prohibit Redis.","Prohibited Redis.","No Redis."
  ])("recognizes the prohibition: %s", text => {
    expect(extractRulePatterns(rule(text))).toContainEqual(expect.objectContaining({kind:"FORBIDDEN_TERM",primaryTerm:"redis"}));
  });

  it.each([
    ["Do not import lodash.","FORBIDDEN_IMPORT","lodash"],
    ["Do not use dependency axios.","FORBIDDEN_DEPENDENCY","axios"],
    ["Use PostgreSQL for persistent storage.","REQUIRED_TECHNOLOGY","postgresql"],
    ["Authentication must go through AuthService.","REQUIRED_ARCHITECTURAL_COMPONENT","AuthService"]
  ])("classifies %s", (text,kind,primaryTerm) => {
    expect(extractRulePatterns(rule(text))).toContainEqual(expect.objectContaining({kind,primaryTerm}));
  });

  it("does not pretend arbitrary prose can be checked deterministically", () => {
    expect(extractRulePatterns(rule("Keep the design elegant and maintainable."))).toEqual([]);
  });

  it.each([
    ["redis","redistemplate"],["mongodb","mongoclient"],["postgresql","postgres"],["jwt","jwt"]
  ])("retains supported %s aliases", (term,alias) => {
    expect(aliasesFor(term)).toContain(alias);
  });
});

describe("Guardian scope matching", () => {
  it.each([
    ["backend","apps/backend/service.java"], ["frontend","apps/frontend/page.tsx"],
    ["extension","apps/extension/background.ts"], ["vscode","apps/vscode-extension/main.ts"],
    ["java","src/Service.java"], ["typescript","src/Page.tsx"],
    ["javascript","src/page.jsx"], ["python","src/app.py"], ["project-wide","src/file.ts"]
  ])("applies %s only within its supported target", (scope,path) => {
    const file={path,previousPath:undefined,staged:false,unstaged:true,untracked:false,deleted:false,renamed:false};
    expect(ruleAppliesToGuardianTarget(rule("Do not use Redis.",scope),{workspaceFolder:{name:"example",fsPath:"/workspace"},changedFiles:[file]})).toBe(true);
  });
});
