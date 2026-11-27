


## Data Safety and Privacy Audit

### Policies to Verify

- **The Policy Spirit**: Users deserve complete transparency regarding what
  personal or sensitive information leaves their devices. Under Play Store
  rules, any off-device transmission of user data must be explicitly declared in
  the Play Store Data Safety form, and sensitive or non-obvious data collection
  requires prior prominent in-app disclosure and affirmative user consent.

- **Common Evaluation Matrix**:
  | Case | Technical Observation | `user_initiated` | `is_third_party` | Disclosure Status | Severity | Issue Summary |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | **1** | **Not Transferred** (Local only) | N/A | N/A | `EXEMPT` | `SUGGESTION` | "Data Safety Compliant (Local-Only)" |
  | **2A** | **Transferred** + **Background** + **No Disclosure** | `false` | (Any) | `MISSING` | `CRITICAL` | "**Silent Background Transfer**": Policy violation. Data is sent without prior disclosure. |
  | **2B** | **Transferred** + **Background** + **Disclosure Found** | `false` | (Any) | `DISCLOSED` | `IMPORTANT` | "**Background Transfer (Disclosure Claimed)**": Evidence found; requires Critic verification. |
  | **3** | **Transferred** + **User-Initiated** + **First Party** | `true` | `false` | `EXEMPT` | `IMPORTANT` | "**User-Initiated Collection**": Disclosure exempt, but **must be declared** in Play Store form. |
  | **4** | **Transferred** + **User-Initiated** + **Third Party** | `true` | `true` | `EXEMPT` | `SUGGESTION` | "**Manual Sharing**": **Policy Exempt**. User triggers transfer to 3P; no disclosure or declaration needed. |

- **Exemptions Reference**:
  - **Anonymous Data**: Fully anonymized data not linked to a user is exempt.
  - **End-to-End Encryption (E2EE)**: Data E2EE where the developer cannot read
    it is exempt.
  - **Open Web WebView**: Data entered into a WebView navigating the open web is
    exempt.
  - *If any of these apply, downgrade severity to `SUGGESTION` and mark
    `EXEMPT`.*

---

### The Audit Protocol

#### Step 0: Facts orientation

- **Identify Third-Party SDKs**: If you observe network, crash-reporting, or
  tracking telemetry, check the project's build files (e.g., build.gradle,
  build.gradle.kts, libs.versions.toml) on-demand using your file-reading tools
  to verify which SDKs (Analytics, Ads, etc.) are integrated.
- **Understand Off-Device Sinks**: Use your native knowledge of standard Android
  framework components to identify transmission destinations. Focus on active
  egress vectors such as standard HTTP/REST libraries (e.g., OkHttp, Retrofit,
  Ktor, HttpURLConnection), WebViews passing data off-device, background syncing
  tasks, or analytic/telemetry frameworks (e.g., Firebase, Mixpanel, Adjust,
  AppsFlyer).
- **Identify Disclosures**: Review `disclosure` below. Use these as starting
  points for Step 2.

#### Step 1: Verify behavioral transmission (Forward Trace)

For each source in `data_sources` listed below, trace the technical signal from
the exact location provided to its transmission endpoint.
- **Mandatory Starting Point**: Navigate to the file and line number specified
  in the `data_sources` list.
- **Loop Discipline**: You must analyze the transmission path for *every* piece
  of evidence listed to verify if it reaches an off-device sink.
- **Evidence**: Provide the file/line where the data is passed to the sink and a
  brief justification of the sink's known transmission behavior (e.g., network
  upload, telemetry payload).
- **Semantic Grounding**: Use the provided `*Description*` to verify that the
  code evidence actually matches the semantic scope of the data type. If the
  variable or logic refers to unrelated data (e.g., a `fileName` that does not
  identify a user `NAME`), treat it as a false positive and follow the
  **Evidentiary Standard** to downgrade or prune the finding.
- **Sink Categorization**: Determine the destination:
  1. **First Party / Service Provider (`is_third_party: false`)**:
     Developer-controlled servers, Firebase, custom APIs, etc.
  2. **Third Party (`is_third_party: true`)**: Social SDKs, ad networks, or
     user-selected apps via Android Share Sheet (`Intent.ACTION_SEND`).
- **Ambiguity Mandate**: If the destination is ambiguous (e.g., generic URL or
  obfuscated SDK), you MUST default to `is_third_party: false` (erring on the
  side of Collection).
- **On-Device Transfer**: Treat silent data sharing to another app via
  Intents/ContentProviders as a transfer (`is_transferred: true`), even if it
  doesn't use the network.

#### Step 2: Semantic Search for Protection (UI Disclosure)

**Conditional Execution**: Execute this step **ONLY IF** `is_transferred` is
`true` AND `user_initiated` is `false`.
- **Rationale**: User-initiated actions (Case 3 and 4) are exempt from Prominent
  Disclosure. Do not search for disclosures if `user_initiated` is true.
- **Semantic Search**: Review the `disclosure` list below.
- **Gatekeeper Check**: Check if the Activity/Fragment displaying that consent
  acts as a gatekeeper (i.e., gates the initialization of the data-collection
  logic). Do not attempt to trace backwards from a low-level repository up to
  the UI.
- **Content Check**: The UI must state *what* is collected and *how* it is used.
- **Evidence**: Provide the file/layout name and the specific text found.

#### Step 3: Synthesis

- **`findings` (CRITICAL)**: You must construct and output exactly **one**
  object inside the `"findings"` array for **every** data type found in the
  `data_sources` list below, regardless of whether it is compliant or a
  violation.
- **Evaluating Keys**: For each data type object in `"findings"`, evaluate and
  populate:
  1.  **psl_constant**: Set this exactly to the uppercase **PSL Constant** of
     the data type (listed as the **Data Type** heading in the **Data Sources to
     Trace** section below, e.g., `"PRECISE_LOCATION"`, `"CONTACTS"`). This is
     critical for downstream matching.
  2.  **policy_id**: Set this exactly to either `"prominent_disclosure_policy"`
     or `"data_safety_section"` depending on the check being performed.
  3.  **is_transferred**: `true` (JSON Boolean) if the data is transferred
     off-device or on-device to a third party. `false` (JSON Boolean) if local
     only.
  4.  **user_initiated**: `true` (JSON Boolean) if the user explicitly triggers
     the transfer.
  5.  **is_third_party**: `true` (JSON Boolean) if the destination is a Third
     Party (e.g., Share Sheet, Social SDK). `false` (JSON Boolean) if it's a
     First Party/Service Provider (e.g., your own backend).
  6.  **prominent_disclosure_status**: Set strictly to one of these three
     uppercase enums: `"DISCLOSED"` (visible disclosure and user consent found),
     `"MISSING"` (no disclosure found, or is not an affirmative gatekeeper), or
     `"EXEMPT"` (exempt from prominent disclosure under policies).
  7.  **purpose**: Categorize why the data is being collected (e.g., "App
     functionality", "Analytics", "Local functionality only").
  8.  **linked_to_user**: `true` (JSON Boolean) if the data is tied to an
     identity, email, or device ID, otherwise `false` (JSON Boolean).

- **Map findings strictly to the 5 Cases:** Use the Case matrix in "Policies to
  Verify" to set the appropriate `severity` and `issue_summary` based on the
  combination of `is_transferred`, `user_initiated`, `is_third_party`, and
  `prominent_disclosure_status`.

---

### Domain-Specific Heuristics

Apply the following heuristics while strictly adhering to the Heuristics &
Extrapolation Boundaries defined in your Execution Mandates:
1. **Implicit Data Leaks (The Logger Loop)**: Inspect if any custom logging
   frameworks (e.g., Crashlytics, custom error loggers, or telemetry SDKs)
   receive sensitive variables as part of diagnostic payloads. If a user
   identifier (e.g., email, account ID) or precise location is passed to a
   logger that uploads payloads off-device, this counts as **transferred**
   (`is_transferred`: true).
2. **Third-Party SDK Siphoning**: Look at the project's build files on-demand
   (e.g., build.gradle or libs.versions.toml). If SDKs like Google Ads, or
   Firebase are initialized and have access to the context, evaluate if
   they are siphoning advertiser IDs or device identifiers automatically. If
   those libraries are loaded and the manifest requests broad network
   permissions, treat those device identifiers as **transferred**
   (`is_transferred`: true) for analytics/marketing purposes.
3. **Indirect Consent**: If you find an `AlertDialog` or disclosure, verify if
   it is an actual gatekeeper. If the app begins tracking user data *prior* to
   the user tapping "Accept", or if the user can close the dialog and continue
   using the app while data tracking remains active, flag this as a `CRITICAL`
   violation under `prominent_disclosure_policy`.

---

### Codebase Context & Evidence

**Semantic Triage Starting Points (Files of Interest)**:
The following files likely contain privacy or consent logic:
- **LEGAL** related:
  - `ic_symbol_privacy_tip.xml`

**Data Sources to Trace**:
- **Data Type**: MUSIC
  *Description: A user’s music files.*
  - `core\common\src\main\res\values-in\runtime_strings.xml (Pattern: song)`
- `feature\backup\src\main\res\values-in\drive_backup_strings.xml (Pattern: song)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentLayoutSizeVisuals.kt (Pattern: track)`
- **Data Type**: FILES_AND_DOCS
  *Description: A user’s files or documents, or information about their files or documents such as file names.*
  - `core\document-ui\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\platform\DocumentActionsHandler.kt (Pattern: application/pdf)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\platform\DocumentToolsHandler.kt (Pattern: application/pdf)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\documents\platform\PdfImportHandler.kt (Pattern: application/pdf)`

**UI Disclosure Starting Points**:
- `app\src\main\java\com\joseleandro\scaniqo\MainActivity.kt (Pattern: Consent)`
- `app\src\main\java\com\joseleandro\scaniqo\ScaniqoApplication.kt (Pattern: collect)`
- `app\src\main\java\com\joseleandro\scaniqo\core\navigation\AppNavigation.kt (Pattern: Dialog)`
- `app\src\main\java\com\joseleandro\scaniqo\core\navigation\DocumentQuickActionsHost.kt (Pattern: AlertDialog)`
- `app\src\main\java\com\joseleandro\scaniqo\core\navigation\FoldAwareAppContent.kt (Pattern: collect)`
- `app\src\main\java\com\joseleandro\scaniqo\ui\documentMerge\DocumentMergeDestination.kt (Pattern: collect)`
- `app\src\main\java\com\joseleandro\scaniqo\ui\toolsHub\ToolsHubScreen.kt (Pattern: collect)`
- `app\src\main\java\com\joseleandro\scaniqo\widget\RecentDocumentsWidget.kt (Pattern: collect)`
- `app\src\main\java\com\joseleandro\scaniqo\widget\WidgetUi.kt (Pattern: collect)`
- `app\src\main\java\com\joseleandro\scaniqo\widget\WidgetUpdater.kt (Pattern: collect)`
- `app\src\main\res\xml\backup_rules.xml (Pattern: share)`
- `app\src\main\res\xml\data_extraction_rules.xml (Pattern: share)`
- `app\src\main\res\xml\document_file_paths.xml (Pattern: share)`
- `core\ads\src\main\java\com\joseleandro\scaniqo\core\ads\AdMobManager.kt (Pattern: Consent)`
- `core\ads\src\main\java\com\joseleandro\scaniqo\core\ads\RewardedAdManager.kt (Pattern: collect)`
- `core\ads\src\main\java\com\joseleandro\scaniqo\ui\ads\DocumentsNativeAd.kt (Pattern: collect)`
- `core\ads\src\main\java\com\joseleandro\scaniqo\ui\ads\ToolAdActionController.kt (Pattern: collect)`
- `core\ads\src\main\java\com\joseleandro\scaniqo\ui\ads\ToolsBannerAd.kt (Pattern: collect)`
- `core\designsystem\src\main\java\com\joseleandro\scaniqo\ui\components\DialogActions.kt (Pattern: AlertDialog)`
- `core\document-ui\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\DocumentActionsViewModel.kt (Pattern: collect)`
- `core\document-ui\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\components\DocumentDeleteDialog.kt (Pattern: AlertDialog)`
- `core\document-ui\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\components\DocumentOverlays.kt (Pattern: Dialog)`
- `core\document-ui\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\components\DocumentPageJumpDialog.kt (Pattern: AlertDialog)`
- `core\document-ui\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\components\DocumentPreviewZoom.kt (Pattern: Dialog)`
- `core\document-ui\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\components\DocumentSourceStep.kt (Pattern: collect)`
- `core\document-ui\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\feedback\rememberDocumentToastState.kt (Pattern: share)`
- `core\document-ui\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\platform\DocumentActionsHandler.kt (Pattern: collect)`
- `core\document-ui\src\main\res\values-ar\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-ar\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-bn\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-bn\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-en\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-en\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-es\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-es\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-fr\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-fr\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-hi\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-hi\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-in\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-in\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-pt\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-pt\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-ru\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-ru\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-zh-rCN\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values-zh-rCN\document_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values\document_platform_strings.xml (Pattern: share)`
- `core\document-ui\src\main\res\values\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-ar\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-ar\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-bn\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-bn\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-en\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-en\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-es\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-es\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-fr\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-fr\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-hi\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-hi\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-in\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-in\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-pt\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-pt\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-ru\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-ru\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-zh-rCN\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values-zh-rCN\onboarding_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values\document_strings.xml (Pattern: share)`
- `core\resources\src\main\res\values\onboarding_strings.xml (Pattern: share)`
- `core\security\src\main\java\com\joseleandro\scaniqo\core\security\SecureWindow.kt (Pattern: Dialog)`
- `core\security\src\main\java\com\joseleandro\scaniqo\ui\security\PasswordSettingsScreen.kt (Pattern: AlertDialog)`
- `core\security\src\main\java\com\joseleandro\scaniqo\ui\security\PasswordUnlockHandler.kt (Pattern: AlertDialog)`
- `core\testing\src\main\java\com\joseleandro\scaniqo\ui\documentCommon\DocumentTestRepositories.kt (Pattern: share)`
- `data\backup\src\main\java\com\joseleandro\scaniqo\data\backup\GoogleDriveBackupRemote.kt (Pattern: Accept)`
- `data\documents\src\main\java\com\joseleandro\scaniqo\data\local\document\DocumentFileStore.kt (Pattern: share)`
- `feature\backup\src\main\java\com\joseleandro\scaniqo\ui\backup\DriveBackupRoute.kt (Pattern: AlertDialog)`
- `feature\backup\src\main\java\com\joseleandro\scaniqo\ui\backup\DriveBackupScreen.kt (Pattern: AlertDialog)`
- `feature\backup\src\main\java\com\joseleandro\scaniqo\ui\backup\DriveBackupViewModel.kt (Pattern: collect)`
- `feature\backup\src\main\java\com\joseleandro\scaniqo\ui\backup\platform\DriveAuthorizationHandler.kt (Pattern: collect)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\DocumentEditorToolRoute.kt (Pattern: AlertDialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\DocumentReaderRoute.kt (Pattern: Dialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\DocumentReaderScreen.kt (Pattern: share)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\DocumentReaderViewModel.kt (Pattern: collect)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\DocumentToolsViewModel.kt (Pattern: collect)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\DocumentViewerRoute.kt (Pattern: collect)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\DocumentViewerViewModel.kt (Pattern: collect)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentFillMarkEditor.kt (Pattern: AlertDialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentInteractiveToolEditor.kt (Pattern: Dialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentLayoutSheetContent.kt (Pattern: AlertDialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentMergeDialogs.kt (Pattern: AlertDialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentOverlayEditor.kt (Pattern: AlertDialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentPagesSheetContent.kt (Pattern: Dialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentPasswordRecoveryDialog.kt (Pattern: AlertDialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentToolPageScope.kt (Pattern: Dialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentToolsSheets.kt (Pattern: AlertDialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\DocumentViewerTopBar.kt (Pattern: share)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\components\FillMarkPropertyDialog.kt (Pattern: AlertDialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentEditor\platform\DocumentToolsHandler.kt (Pattern: Dialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentSignature\DocumentSignatureAdjustmentPanel.kt (Pattern: collect)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentSignature\DocumentSignatureRoute.kt (Pattern: AlertDialog)`
- `feature\editor\src\main\java\com\joseleandro\scaniqo\ui\documentSignature\DocumentSignatureScreen.kt (Pattern: Dialog)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\documentSearch\DocumentSearchRoute.kt (Pattern: collect)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\documentSearch\DocumentSearchViewModel.kt (Pattern: collect)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\documents\DocumentsRoute.kt (Pattern: collect)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\documents\DocumentsViewModel.kt (Pattern: collect)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\documents\components\DocumentCard.kt (Pattern: share)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\documents\components\PdfImportDialogs.kt (Pattern: AlertDialog)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\documents\platform\PdfImportHandler.kt (Pattern: Dialog)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\tags\TagsRoute.kt (Pattern: collect)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\tags\TagsScreen.kt (Pattern: AlertDialog)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\tags\TagsViewModel.kt (Pattern: collect)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\trash\TrashRoute.kt (Pattern: collect)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\trash\TrashScreen.kt (Pattern: AlertDialog)`
- `feature\library\src\main\java\com\joseleandro\scaniqo\ui\trash\TrashViewModel.kt (Pattern: collect)`
- `feature\onboarding\src\main\java\com\joseleandro\scaniqo\ui\onboarding\OnboardingIllustration.kt (Pattern: collect)`
- `feature\onboarding\src\main\java\com\joseleandro\scaniqo\ui\onboarding\OnboardingRoute.kt (Pattern: collect)`
- `feature\onboarding\src\main\java\com\joseleandro\scaniqo\ui\onboarding\OnboardingScreen.kt (Pattern: share)`
- `feature\onboarding\src\main\res\values-ar\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values-bn\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values-en\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values-es\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values-fr\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values-hi\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values-in\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values-pt\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values-ru\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values-zh-rCN\onboarding_strings.xml (Pattern: share)`
- `feature\onboarding\src\main\res\values\onboarding_strings.xml (Pattern: share)`
- `feature\pdf-tools\src\main\java\com\joseleandro\scaniqo\ui\documentCertificates\DocumentCertificatesRoute.kt (Pattern: collect)`
- `feature\pdf-tools\src\main\java\com\joseleandro\scaniqo\ui\pdfTools\PdfUtilityRoute.kt (Pattern: collect)`
- `feature\pdf-tools\src\main\java\com\joseleandro\scaniqo\ui\pdfTools\PdfUtilityViewModel.kt (Pattern: collect)`
- `feature\pdf-tools\src\main\java\com\joseleandro\scaniqo\ui\pdfTools\PdfUtilityWorkspace.kt (Pattern: Dialog)`
- `feature\pdf-tools\src\main\res\values-en\utility_workspace_strings.xml (Pattern: privacy)`
- `feature\pdf-tools\src\main\res\values\utility_workspace_strings.xml (Pattern: privacy)`
- `feature\premium\src\main\java\com\joseleandro\scaniqo\ui\premium\PremiumRoute.kt (Pattern: collect)`
- `feature\settings\src\main\java\com\joseleandro\scaniqo\ui\settings\SettingsScreen.kt (Pattern: AlertDialog)`
- `feature\settings\src\main\res\values-ar\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values-bn\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values-en\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values-es\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values-fr\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values-hi\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values-in\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values-pt\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values-ru\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values-zh-rCN\ads_strings.xml (Pattern: privacy)`
- `feature\settings\src\main\res\values\ads_strings.xml (Pattern: privacy)`
- `feature\signatures\src\main\java\com\joseleandro\scaniqo\ui\signatures\SignatureEditorScreen.kt (Pattern: AlertDialog)`
- `feature\signatures\src\main\java\com\joseleandro\scaniqo\ui\signatures\SignatureImageFlowScreen.kt (Pattern: Dialog)`
- `feature\signatures\src\main\java\com\joseleandro\scaniqo\ui\signatures\SignaturesScreen.kt (Pattern: AlertDialog)`
- `feature\signatures\src\main\java\com\joseleandro\scaniqo\ui\signatures\SignaturesViewModel.kt (Pattern: Dialog)`
- `feature\signatures\src\main\java\com\joseleandro\scaniqo\ui\signatures\crop\SignatureCropDialog.kt (Pattern: Dialog)`

## Output schema

Save final JSON output to `C:\Users\leandro\Desktop\projetos\pessoais\projetos\Scaniqo\.scratch\play_policy_insights_caf5d15b-7ef6-4b9d-857f-986ca576ff2a/worker_data_safety_part_3.json`.

```json
{
  "domain": "Data Safety and Privacy",
  "findings": [
    {
      "psl_constant": "STRING_VALUE (The exact PSL Constant, e.g., USER_ACCOUNT)",
      "policy_id": "prominent_disclosure_policy | data_safety_section",
      "issue_summary": "STRING_VALUE",
      "severity": "CRITICAL | IMPORTANT | SUGGESTION",
      "files_involved": ["STRING_VALUE"],
      "evidence": "STRING_VALUE",
      "recommendation": "STRING_VALUE",

      # UNIFIED DATA SAFETY METADATA LOOKUP KEYS:
      "is_transferred": true | false,
      "user_initiated": true | false,
      "is_third_party": true | false,
      "prominent_disclosure_status": "DISCLOSED | MISSING | EXEMPT",
      "purpose": "STRING_VALUE",
      "linked_to_user": true | false
    }
  ]
}
```

# Execution Mandates

### Technical Rules

1.  **Absolute Paths Only**: Always resolve and use absolute paths.
2.  **Containment**: Write all artifacts strictly within `C:\Users\leandro\Desktop\projetos\pessoais\projetos\Scaniqo\.scratch\play_policy_insights_caf5d15b-7ef6-4b9d-857f-986ca576ff2a`.
3.  **Fail-fast**: If any required input file is missing, stop immediately and
    report the failure.

### Surgical Input Protocol & Efficient Search (MANDATORY)

-   **Direct Evidence First**: Prioritize files listed in the **Context &
    Evidence** sections. Use the provided file/line evidence (e.g., from Data
    Sources or Sinks) to jump directly to the relevant code. Do not perform
    broad workspace searches if these surgical starting points are available.
-   **Path Filtering Over File Crawling**: Locate target files by name, path, or
    extension *first* using directory/file listing tools before performing any
    text/content-based searches. Restrict searches and file reads strictly to
    the target `C:\Users\leandro\Desktop\projetos\pessoais\projetos\Scaniqo`.
-   **Strict Exclusions (The Noise Wall)**: Configure search, glob, and find
    tools to ignore build, cache, dependencies, and testing folders. You MUST
    exclude matches from: `**/build/**`, `**/.gradle/**`, `**/.scratch/**`,
    `**/androidTest/**`, `**/test/**`, `**/node_modules/**`.
-   **Targeted Extensions**: Restrict content searches and file reads strictly
    to source and configuration files: `.java`, `.kt`, `.xml`, `.gradle`, `.kts`
    (and `.js`, `.ts`, `.jsx`, `.tsx`, `.dart` if a hybrid/cross-platform
    environment is analyzed). Never search or read inside compiled `.class`
    files, binary resources, or output assets.
-   **Surgical Queries & Limiters**: Use highly specific search patterns (e.g.,
    search for `getLastKnownLocation` or `deleteAccount` instead of general
    words like `location` or `delete`). If search tools support limits or
    pagination, cap results at a maximum of 50 matches. Do not load unlimited
    search results into your context window.
-   **Parallel Reading Required (Turn Efficiency)**: You are operating under a
    strict maximum turn limit. To prevent timeouts, you MUST request to read
    multiple target files concurrently in a single response. Do not read the
    evidence files sequentially one-by-one. Issue all of your file-reading tool
    calls simultaneously whenever possible.

### Evidentiary Standard & Guardrails (CRITICAL)

To prevent over-auditing, false positives, and speculative "prosecution" of
compliant code during extrapolation:

1.  **Presumption of Compliance**: Treat code as compliant unless there is
    *definitive, visible evidence* in the provided files of a policy violation.
    If code is ambiguous, or if network/database logic is hidden behind
    abstractions (e.g., calling an interface or repository method like
    `clearSession()`), you must assume standard compliant behavior. Do NOT guess
    or speculate about what happens behind interfaces.
2.  **Benefit of the Doubt**: When compliance cannot be strictly verified due to
    code abstractions or missing source file contexts, you must downgrade your
    finding:
    -   Never flag a `🔴 Critical` or `🟡 Important` finding based on suspicion or
        lack of context.
    -   Instead, output a `🔵 Suggestion` (informational) to advise the developer
        on what to double-check in their backend or configuration.
3.  **Exclusion of Local State**: Local-only processing (e.g., caching theme
    settings, user-selected visual configurations, or on-device-only database
    operations) is explicitly exempt from Data Safety collection or Account
    Deletion mandates.
4.  **Concrete Attributions**: Every `🔴 Critical` or `🟡 Important` finding must
    cite the exact file, line number, or configuration block containing the
    direct violation. If you cannot cite the exact line of code containing the
    violation, you cannot flag it as a violation.
5.  **Empty-List Discipline**: If no policy violations, discrepancies, or review
    items are identified during your audit, you MUST represent this as an empty
    array `[]` for that field (e.g., `"findings": []`, `"verified_findings":
    []`, or `"manual_verification_required": []`). **DO NOT** populate arrays
    with "dummy" objects, placeholder strings, or `"N/A"` / `"None"` values.
6.  **Heuristics & Extrapolation Boundaries**: Whenever applying specific
    heuristics defined in your goal (e.g., searching for implicit logger leaks
    or
    SDK siphoning), you must strictly bound them to the provided evidence and
    their immediate callers. You are strictly forbidden from initiating broad,
    unbounded searches for custom paths or variables across the wider codebase.
    Base your extrapolation only within the specific files already provided to
    you in the prompt.

### Finalization & Output Mandates (CRITICAL)

-   **Iterative Saving**: If your investigation requires multiple steps, save
    partial or intermediate JSON states to disk as you progress. Do not hold all
    data in memory until the very end to prevent data loss upon interruption.
-   **Strict File Output (NO TRIPLE BACKTICKS)**: You MUST save your final JSON
    output to disk at the exact path specified in the goal schema using your
    file-writing capabilities.
    -   **CRITICAL: The content written to the file MUST be pure, raw JSON. DO
        NOT wrap the contents inside the JSON file with Markdown code blocks
        (such as triple backticks `json ...`). Writing markdown blocks into the
        file makes the JSON unparseable by the compiler.**
-   **NO Chat Summaries**: **MANDATORY: DO NOT summarize your findings, explain
    your reasoning, or output JSON in your final chat response.** Your chat
    output wastes context and is ignored by the orchestrator.
-   **Verification Before Termination**: You MUST only terminate and return the
    "SUCCESS" string *after* you have explicitly verified that your JSON file
    successfully wrote to disk and contains valid JSON (e.g., by reading the
    file back or checking the directory contents).
-   **Final response**: Your final response MUST be exactly the word: "SUCCESS"
    and nothing else.
