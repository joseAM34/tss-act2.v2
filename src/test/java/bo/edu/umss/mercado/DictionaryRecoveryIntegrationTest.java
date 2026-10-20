package bo.edu.umss.mercado;

import bo.edu.umss.mercado.statistics.TextDictionaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:dictionary-recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@WithMockUser(username="recovery-admin",roles="ADMIN")
class DictionaryRecoveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TextDictionaryService dictionaries;
    void alter(){
        dictionaries.saveGlobal(0,"Tema=original","recovery-admin");
        jdbc.update("UPDATE universal_dictionary_revision SET rules_text=? WHERE revision=1","Tema=alterado");
    }
    @Test void confirmationCreatesVerifiedRevisionAndPreservesEvidence()throws Exception{
        alter();var state=dictionaries.recovery();
        mvc.perform(get("/admin/diccionario")).andExpect(status().isOk()).andExpect(view().name("dictionary-recovery"));
        mvc.perform(post("/admin/diccionario/recuperar").with(csrf()).param("snapshot",state.snapshot()).param("confirmed","true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/diccionario"));
        assertThat(dictionaries.global().revision()).isEqualTo(2);
        assertThat(dictionaries.global().rules()).contains("alterado");
        assertThat(dictionaries.recovery()).isNull();
        assertThat(jdbc.queryForObject("SELECT rules_hash FROM universal_dictionary_revision WHERE revision=1",String.class)).isEqualTo(state.storedHash());
        assertThat(jdbc.queryForObject("SELECT original_rules FROM universal_dictionary_recovery",String.class)).isEqualTo(state.rules());
        assertThat(jdbc.queryForObject("SELECT actor FROM universal_dictionary_recovery",String.class)).isEqualTo("recovery-admin");
        mvc.perform(get("/admin/diccionario")).andExpect(status().isOk()).andExpect(view().name("text-suggestions"));
    }
    @Test void recoveryRequiresAdminCsrfConfirmationAndUnchangedContent()throws Exception{
        alter();String snapshot=dictionaries.recovery().snapshot();
        mvc.perform(get("/admin/diccionario/recuperar").with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/diccionario/recuperar").param("snapshot",snapshot).param("confirmed","true")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/diccionario/recuperar").with(csrf()).with(user("viewer").roles("USER")).param("snapshot",snapshot).param("confirmed","true")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/diccionario/recuperar").with(csrf()).param("snapshot",snapshot)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE universal_dictionary_revision SET rules_text=? WHERE revision=1","Otro=cambio");
        mvc.perform(post("/admin/diccionario/recuperar").with(csrf()).param("snapshot",snapshot).param("confirmed","true")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM universal_dictionary_recovery",Integer.class)).isZero();
    }
    @Test void invalidRulesRemainBlockedWithoutCreatingARevision()throws Exception{
        alter();jdbc.update("UPDATE universal_dictionary_revision SET rules_text=? WHERE revision=1","sin separador");
        mvc.perform(post("/admin/diccionario/recuperar").with(csrf()).param("snapshot",dictionaries.recovery().snapshot()).param("confirmed","true")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM universal_dictionary_revision",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM universal_dictionary_recovery",Integer.class)).isZero();
    }
}
