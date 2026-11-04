package com.joseleandro.scaniqo.core.localization

import com.joseleandro.scaniqo.core.common.R
import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
/**
 * Presentation boundary for repositories that still report legacy text errors.
 * User-authored names/content never pass through this adapter. Unknown technical
 * failures use a localized, safe fallback instead of displaying exception details.
 */
@StringRes
internal fun runtimeMessageResource(message: String): Int = when (message) {
    "Minhas assinaturas" -> CoreResourcesR.string.runtime_signatures_title
    "Voltar" -> CoreResourcesR.string.runtime_back
    "Nova assinatura" -> CoreResourcesR.string.runtime_new_signature
    "Desenhe, digite ou importe uma assinatura para aplicar nos seus PDFs." -> CoreResourcesR.string.runtime_signatures_body
    "Não foi possível carregar as assinaturas." -> CoreResourcesR.string.runtime_signatures_load_error
    "Tentar novamente" -> CoreResourcesR.string.runtime_retry
    "Sua biblioteca está vazia. Crie a primeira assinatura." -> R.string.runtime_signatures_empty
    "Renomear assinatura" -> R.string.runtime_signature_rename
    "Excluir assinatura" -> CoreResourcesR.string.runtime_signature_delete
    "Excluir assinatura?" -> CoreResourcesR.string.runtime_signature_delete_title
    "Excluir" -> CoreResourcesR.string.runtime_delete
    "Cancelar" -> CoreResourcesR.string.runtime_cancel
    "Nome" -> R.string.runtime_name
    "Salvar" -> CoreResourcesR.string.runtime_save
    "Criar assinatura" -> CoreResourcesR.string.runtime_signature_create
    "Nome na biblioteca" -> CoreResourcesR.string.runtime_library_name
    "Desenhar" -> CoreResourcesR.string.runtime_draw
    "Digitar" -> CoreResourcesR.string.runtime_type
    "Imagem" -> R.string.runtime_image
    "Desfazer" -> CoreResourcesR.string.runtime_undo
    "Limpar" -> CoreResourcesR.string.runtime_clear
    "Espessura" -> CoreResourcesR.string.runtime_thickness
    "Texto da assinatura" -> CoreResourcesR.string.runtime_signature_text
    "Sua assinatura" -> CoreResourcesR.string.runtime_signature_placeholder
    "Cursiva" -> CoreResourcesR.string.runtime_font_cursive
    "Manuscrita" -> CoreResourcesR.string.runtime_font_handwritten
    "Clássica" -> CoreResourcesR.string.runtime_font_classic
    "Monoespaçada" -> CoreResourcesR.string.runtime_font_monospace
    "Escolher imagem" -> R.string.runtime_image_choose
    "Trocar imagem" -> R.string.runtime_image_change
    "Remover fundo branco ao salvar" -> R.string.runtime_remove_white
    "PNGs transparentes mantêm a transparência. Imagens de até 10 MB." -> R.string.runtime_signature_image_hint
    "Preto" -> CoreResourcesR.string.runtime_black
    "Azul" -> CoreResourcesR.string.runtime_blue
    "Violeta" -> CoreResourcesR.string.runtime_violet
    "Salvando…" -> R.string.runtime_saving
    "Salvar assinatura" -> CoreResourcesR.string.runtime_signature_save
    "Desenhe aqui" -> CoreResourcesR.string.runtime_draw_here
    "Imagem indisponível" -> CoreResourcesR.string.runtime_image_unavailable
    "Importando PDF" -> CoreResourcesR.string.runtime_pdf_import_title
    "Preparando o documento para edição…" -> CoreResourcesR.string.runtime_pdf_import_progress
    "Este PDF tem senha" -> CoreResourcesR.string.runtime_pdf_password_title
    "Informe a senha atual do arquivo. A proteção será mantida na cópia importada." -> CoreResourcesR.string.runtime_pdf_password_body
    "Senha do PDF" -> CoreResourcesR.string.runtime_pdf_password
    "Configure a senha geral para guardar este acesso com a proteção do aparelho." -> CoreResourcesR.string.runtime_pdf_password_setup
    "Gerenciar senha geral" -> CoreResourcesR.string.runtime_password_manage
    "Importar" -> CoreResourcesR.string.runtime_import
    "Não foi possível importar" -> CoreResourcesR.string.runtime_import_failed
    "Fechar" -> CoreResourcesR.string.runtime_close
    "Não foi possível guardar o acesso. Desbloqueie o cofre e tente novamente." -> CoreResourcesR.string.runtime_recovery_failed
    "Usar senha do PDF" -> CoreResourcesR.string.runtime_recovery_title
    "Informe a senha usada para proteger este arquivo. O acesso será guardado novamente no cofre do aparelho." -> CoreResourcesR.string.runtime_recovery_body
    "Senha original do PDF" -> CoreResourcesR.string.runtime_recovery_password
    "Configure ou recupere o cofre antes de continuar." -> CoreResourcesR.string.runtime_recovery_setup
    "Abrindo…" -> CoreResourcesR.string.runtime_opening
    "Abrir documento" -> CoreResourcesR.string.runtime_open_document
    "PDF protegido por senha" -> CoreResourcesR.string.runtime_password_protected
    "Não foi possível concluir a operação. Tente novamente." -> R.string.runtime_operation_failed
    "Assinatura salva." -> R.string.runtime_signature_saved
    "Assinatura excluída." -> R.string.runtime_signature_deleted
    "Assinatura renomeada." -> R.string.runtime_signature_renamed
    "Limite de traços atingido. Desfaça um traço para continuar." -> R.string.runtime_signature_stroke_limit
    "Escolha uma imagem da assinatura." -> R.string.runtime_signature_image_required
    "Informe a senha do PDF." -> R.string.runtime_password_required
    "Desbloqueie para acessar os PDFs protegidos." -> R.string.runtime_unlock_required
    "A mesclagem foi pausada. Toque em tentar novamente para continuar." -> R.string.runtime_merge_paused
    "Configure a senha geral para guardar a senha deste PDF com segurança." -> R.string.runtime_password_setup_required
    "Senha incorreta. Tente novamente." -> R.string.runtime_password_incorrect
    "PDF assinado salvo." -> R.string.runtime_signed_pdf_saved
    "Imagens exportadas em um arquivo ZIP." -> CoreResourcesR.string.runtime_zip_exported
    "Dados revisados exportados em CSV." -> R.string.runtime_csv_exported
    "Informe uma página válida do comprovante." -> R.string.runtime_receipt_page_required
    "Revise os dados novamente antes de exportar." -> R.string.runtime_receipt_review_required
    "O documento não está mais disponível." -> R.string.runtime_document_unavailable
    "Documento importado" -> R.string.runtime_imported_document
    "Minha assinatura" -> R.string.runtime_signature_default_name
    "Informe um nome de até 100 caracteres." -> R.string.runtime_name_invalid
    "Selecione um PDF de até 50 MB." -> R.string.runtime_pdf_size_limit
    "Selecione um PDF com 1 a 100 páginas." -> R.string.runtime_pdf_page_limit
    "Abra uma cópia sem senha para usar esta ferramenta." -> R.string.runtime_pdf_unprotected_required
    "Escolha um arquivo novo para preservar o PDF original." -> R.string.runtime_new_file_required
    "Não foi possível salvar no local selecionado." -> R.string.runtime_destination_failed
    "Selecione novamente o certificado no Android." -> R.string.runtime_certificate_select_again
    "A cadeia do certificado não está disponível." -> R.string.runtime_certificate_chain_unavailable
    "Selecione um certificado RSA ou EC." -> R.string.runtime_certificate_rsa_ec
    "Não foi possível usar este certificado para assinar." -> R.string.runtime_certificate_sign_failed
    "Este documento não permite uma nova assinatura nesta ferramenta." -> R.string.runtime_certificate_sign_disallowed
    "A senha deste PDF não permite copiar páginas para outro documento." -> R.string.runtime_pdf_copy_disallowed
    "Este PDF não permite extrair conteúdo." -> R.string.runtime_pdf_extract_disallowed
    "A senha informada não permite editar este PDF." -> R.string.runtime_pdf_edit_disallowed
    "Selecione pelo menos dois PDFs." -> R.string.runtime_two_pdfs_required
    "Digitalize entre 1 e 20 páginas por vez." -> R.string.runtime_scan_page_limit
    "As imagens excederam 200 MB. Exporte menos páginas por vez." -> R.string.runtime_images_size_limit
    "Nenhum texto foi reconhecido. Tente uma digitalização mais nítida." -> R.string.runtime_ocr_empty
    "Desenhe ou digite uma assinatura antes de salvar." -> R.string.runtime_signature_empty
    "Selecione uma imagem de até 10 MB." -> R.string.runtime_image_size_limit
    "PDF selecionado" -> CoreResourcesR.string.runtime_selected_pdf
    "Informe um nome com até 80 caracteres." -> R.string.runtime_signature_name_invalid
    "Digite um nome com até 80 caracteres." -> R.string.runtime_signature_text_invalid
    "O desenho está muito grande. Simplifique a assinatura." -> R.string.runtime_signature_drawing_large
    "Nenhum traço foi encontrado. Ajuste o recorte ou use uma imagem mais nítida." -> R.string.signature_import_error_no_ink
    "Não foi possível processar a assinatura. Ajuste o recorte e tente novamente.",
    "O processamento não produziu transparência.",
    "O processamento não produziu uma assinatura válida." -> R.string.signature_import_error_process_failed
    "Confirme o recorte e aguarde o processamento da assinatura." -> R.string.signature_import_error_process_required
    "Use uma imagem de até 40 MB." -> R.string.signature_import_error_size_limit
    "Selecione novamente a imagem da assinatura." -> R.string.signature_import_error_select_again
    "Escolha uma imagem válida." -> R.string.runtime_image_invalid
    "Assinatura não encontrada." -> R.string.runtime_signature_not_found
    "Assinatura excluída" -> R.string.runtime_signature_deleted
    "Assinatura renomeada" -> R.string.runtime_signature_renamed
    "Assinatura salva" -> R.string.runtime_signature_saved
    "Assinatura atualizada" -> R.string.runtime_signature_updated
    "Informe a senha deste PDF." -> R.string.runtime_password_required
    "Senha incorreta. Confira a senha do PDF." -> R.string.runtime_password_incorrect
    "Use uma imagem de até 10 MB." -> R.string.runtime_image_size_limit
    "Crie uma assinatura antes de salvar." -> R.string.signature_editor_empty
    "Informe um nome para a assinatura." -> R.string.runtime_signature_name_invalid
    "Não foi possível separar a assinatura do papel. Recorte mais perto, melhore a iluminação ou refaça a foto com foco." -> R.string.signature_image_quality_retry
    "Não foi possível processar a assinatura. Tente outra imagem." -> R.string.signature_import_error_process_failed
    "A assinatura está vazia." -> R.string.runtime_signature_empty
    "Desenhe a assinatura antes de salvar." -> R.string.runtime_signature_empty
    "O PDF excede o limite de 50 MB." -> R.string.runtime_pdf_size_limit
    "As fontes do documento excedem 50 MB." -> R.string.runtime_pdf_size_limit
    "O resultado excedeu o limite de 50 MB." -> R.string.runtime_pdf_size_limit
    "O PDF deve ter entre 1 e 100 páginas." -> R.string.runtime_pdf_page_limit
    "O arquivo não está disponível." -> R.string.runtime_document_unavailable
    "Configure uma senha geral primeiro." -> R.string.runtime_password_setup_required
    "A assinatura contém intervalos de bytes inválidos." -> R.string.certificate_invalid_ranges
    "Este formato de assinatura não é compatível com a verificação local." -> R.string.certificate_unsupported_format
    "A verificação local aceita assinaturas com SHA-256, SHA-384 ou SHA-512." -> R.string.certificate_supported_hashes
    "Há bytes posteriores à revisão assinada." -> R.string.certificate_later_bytes
    "Não foi possível confirmar a integridade desta assinatura." -> R.string.certificate_integrity_failed
    "Selecione no máximo 20 imagens." -> R.string.runtime_image_count_limit
    "Não foi possível abrir o scanner." -> CoreResourcesR.string.tools_scan_error
    "Não foi possível abrir o seletor de arquivos." -> CoreResourcesR.string.certificate_file_error
    "Use páginas ou intervalos, por exemplo: 1, 3-5." -> CoreResourcesR.string.utility_pages_hint
    else -> R.string.runtime_operation_failed
}

@Composable
fun localizedRuntimeMessage(message: String): String {
    val pageLimit = Regex("^Escolha páginas entre 1 e ([0-9]+), em intervalos crescentes\\.$")
        .matchEntire(message)?.groupValues?.get(1)?.toIntOrNull()
    return if (pageLimit != null) stringResource(R.string.pages_move_range, pageLimit)
        else stringResource(runtimeMessageResource(message))
}
