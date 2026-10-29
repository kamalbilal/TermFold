# Spec Delta

## Purpose

Lets users inspect PDFs, run deterministic toolkit operations, and generate new PDFs from templates they own, with an authorization gate and a verified delivery package—without editing third-party issued documents or covering old text.

## ADDED Requirements

### Requirement: Workflows lists PDF Tools

The system SHALL show a **PDF Tools** section on the Workflows home list alongside Automation, PR Reviewer, Product Manager, and More.

#### Scenario: Open PDF Tools

- **WHEN** the user opens Workflows and selects PDF Tools
- **THEN** the app shows the PDF Tools section (toolkit plus job sessions), not Automation projects or More

### Requirement: Toolkit without an agent

The PDF Tools section SHALL offer deterministic toolkit actions that do not start an agent: merge, split, rotate, extract text, extract images, and images-to-PDF. Each action SHALL write a new file and SHALL NOT overwrite the input file.

#### Scenario: Merge two PDFs

- **WHEN** the user picks two PDFs and runs merge
- **THEN** the system writes a new merged PDF and leaves both inputs unchanged

#### Scenario: Split without chat

- **WHEN** the user splits a PDF by page ranges
- **THEN** the system produces the new files without opening an agent session

### Requirement: Document-centric job sessions

PDF Tools jobs SHALL persist as sessions bound to a session directory of document copies and artifacts, not to a git workspace copy. The system SHALL NOT require a coding folder to start a job.

#### Scenario: Start without a coding project

- **WHEN** the user starts an Inspect or Generate job and no coding folder is selected
- **THEN** the session is created and opens on its document directory

### Requirement: Import copies the original

Importing a PDF via the system file picker, share-in, or the Files tab SHALL copy bytes into the session directory, record a SHA-256 of the copy, and SHALL NOT modify the user’s original file.

#### Scenario: Import from picker

- **WHEN** the user imports a PDF into a new job
- **THEN** the session directory contains a copy whose SHA-256 is stored, and the source file is unchanged

### Requirement: Eager inspect before chat

When a job has an imported PDF, the system SHALL run inspect (page count, encryption, signatures, forms, per-page kind, font summary, page renders) and SHALL surface that result before the user must send an agent message. A Generate job with no imported PDF SHALL NOT wait on inspect of a source file.

#### Scenario: Inspect chips on open

- **WHEN** the user opens an Inspect job after import completes
- **THEN** the session shows page preview and classification chips without waiting for an agent reply

#### Scenario: Generate without a source PDF

- **WHEN** the user starts Generate and does not import a PDF
- **THEN** the session opens the template form and does not require inspect chips for a source document

#### Scenario: Encrypted without password

- **WHEN** inspect finds encryption and no password is available
- **THEN** the system asks for the password and does not attempt to decrypt by other means

### Requirement: Inspect job does not mutate

An Inspect job SHALL let the user browse pages, extracted text, and a shareable inspect report. It SHALL NOT write an edited PDF.

#### Scenario: Inspect only

- **WHEN** the user completes an Inspect job
- **THEN** the session contains inspect artifacts and no mutated output PDF

### Requirement: Generate from owned templates

A Generate job SHALL produce a new PDF from a named template the user owns plus field values from a form (and optional CSV for one batch file). Field values SHALL come from that form or CSV, not from chat. Confirming Generate on the form SHALL run without an agent turn. An agent MAY help with layout; it SHALL NOT be required to produce the file.

#### Scenario: Generate from form without an agent

- **WHEN** the user picks an owned template, fills required fields, and confirms Generate
- **THEN** the system writes a new PDF with those field values and embedded fonts without an agent message

#### Scenario: Chat does not override fields

- **WHEN** the user types a different name in chat than the form field
- **THEN** generate uses the form field value

### Requirement: Authorization class is required

Every job that can produce or change a PDF SHALL require an authorization class chosen in the UI: `OWN_TEMPLATE`, `OWN_DATA_DOC`, `THIRD_PARTY_ISSUED`, or `UNKNOWN`. The class SHALL be stored on the session before apply.

#### Scenario: Class missing

- **WHEN** the user tries to apply generate or a mutating toolkit action without a class
- **THEN** the system blocks the action and asks for a class

### Requirement: Refuse third-party issued documents

The system SHALL refuse to alter PDFs classified as `THIRD_PARTY_ISSUED` when they are financial, identity, legal, or credential documents issued by someone else. It SHALL tell the user they may start Generate from a template they own, or ask the issuer for a correction. It SHALL NOT add a request-letter job type. It SHALL NOT copy another organization’s logo, letterhead, or signature onto a changed document.

#### Scenario: Payslip issued by an employer

- **WHEN** the user classifies a payslip as third-party issued and asks to change a figure
- **THEN** the system refuses the edit, does not write a changed payslip, and shows that they can Generate from their own template or contact the issuer

#### Scenario: Unknown class treated as blocked mutate

- **WHEN** the class is `UNKNOWN` and the user requests an edit
- **THEN** the system does not apply the edit until the class is `OWN_TEMPLATE` or `OWN_DATA_DOC`

### Requirement: Overlay edits are forbidden

The system SHALL NOT implement covering existing text with a filled rectangle and drawing new text as a document edit. Sensitive removal SHALL remove content, not hide it.

#### Scenario: Agent requests overlay

- **WHEN** a plan or tool call specifies overlay cover-up of existing text
- **THEN** the system rejects the plan and does not write that output

### Requirement: Plan approval before agent mutations

When an agent proposes operations beyond form Generate, the system SHALL show a structured plan of operations and risks as cards and SHALL apply those operations only after the user approves. Confirming the Generate form SHALL count as approval for that form generate. The system SHALL NOT apply agent operations from chat alone.

#### Scenario: Form generate needs no plan cards

- **WHEN** the user confirms Generate on a filled form with no extra agent operations
- **THEN** the system writes the PDF without waiting for plan cards or an agent turn

#### Scenario: Dismiss agent plan

- **WHEN** the agent showed plan cards and the user does not approve
- **THEN** no output PDF is written from those agent operations

### Requirement: Verified delivery package

A successful generate SHALL deliver a package: output PDF, change report, hashes of inputs actually used and of the output, and page renders for visual check. Generated files SHALL be labeled as not an original issued document. If inspect found digital signatures on an imported PDF, the inspect report SHALL state that modifying that file would invalidate the signatures.

#### Scenario: Share output

- **WHEN** the user shares a successful generate result
- **THEN** the share includes the output PDF and the report stating it is not an original issued document

#### Scenario: Signed file on inspect

- **WHEN** inspect finds digital signatures on an imported PDF
- **THEN** the inspect report states the file is signed and that edits would invalidate the signatures

### Requirement: Agent PDF tools are gated

When a PDF Tools job session is active, the agent SHALL use the PDF Tools MCP tools for inspect, model, plan, apply, verify, and refuse. Apply SHALL fail if authorization is missing or refused, if the plan was not user-approved, or if verify would fail the session’s acceptance checks.

#### Scenario: Apply without approval

- **WHEN** the agent calls apply before the user approved the plan
- **THEN** the tool returns an error and no PDF is written

#### Scenario: Verify mismatch

- **WHEN** verify finds text or pixel changes outside the approved operations
- **THEN** the job is not delivered as success

### Requirement: Session privacy

The system SHALL NOT log PDF body text in app or agent debug logs. Deleting a PDF Tools session SHALL delete its session directory of copies and artifacts.

#### Scenario: Delete session

- **WHEN** the user deletes a PDF Tools session
- **THEN** the session directory is removed from device storage

### Requirement: Share PDF offers Inspect or a project

When the user shares a PDF into TermFold, the system SHALL let them start a PDF Tools Inspect job with a copy, or save into a coding project as share already does. It SHALL NOT only copy into a project with no PDF Tools option.

#### Scenario: Share to Inspect

- **WHEN** the user shares an `application/pdf` file and chooses PDF Tools Inspect
- **THEN** a new Inspect job is created with a copy of the file and the source is unchanged

#### Scenario: Share to project still available

- **WHEN** the user shares a PDF and chooses a coding project
- **THEN** the file is copied into that project as existing share behavior

### Requirement: Import size and page limits

The system SHALL reject PDF imports that exceed its size limit or page-count limit, show an error that names those limits, and SHALL NOT keep a session copy of the rejected file.

#### Scenario: Over page limit

- **WHEN** the user imports a PDF with more pages than the import page limit
- **THEN** import fails with an error that includes the page limit and no input copy remains in a session directory

### Requirement: Untrusted PDFs are not executed

The system SHALL treat imported PDFs as untrusted data. It SHALL NOT execute JavaScript, `/OpenAction`, or `/Launch` from a PDF.

#### Scenario: PDF with launch action

- **WHEN** inspect or toolkit runs on a PDF that contains `/Launch` or JavaScript actions
- **THEN** those actions are not executed and inspect still reports structure
