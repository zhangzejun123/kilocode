import type { KilocodeSessionImportProjectData as Project } from "@kilocode/sdk/v2"
import type { LegacyHistoryItem } from "./legacy-types"
import { createProjectID } from "./ids"

export function createProject(item?: LegacyHistoryItem): NonNullable<Project["body"]> {
  const project = makeProject()

  project.id = createProjectID(item?.workspace)

  project.worktree = item?.workspace ?? ""

  project.sandboxes = item?.workspace ? [item.workspace] : []

  project.timeCreated = item?.ts ?? 0

  project.timeUpdated = item?.ts ?? 0

  return project
}

function makeProject(): NonNullable<Project["body"]> {
  return {
    id: "",
    worktree: "",
    sandboxes: [],
    timeCreated: 0,
    timeUpdated: 0,
  }
}
