package server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserManagerTest {

    @Test
    void testRegister() {
        UserManager userManager = new UserManager(10);

        // Testa registo bem-sucedido
        assertTrue(userManager.register("user1", "password"));

        // Testa registo duplicado
        assertFalse(userManager.register("user1", "password"));
    }

    @Test
    void testAuthenticate() throws InterruptedException {
        UserManager userManager = new UserManager(10);

        // Registar utilizador
        userManager.register("user1", "password");

        // Testar autenticação com credenciais corretas
        assertEquals(1, userManager.authenticate("user1", "password"));

        // Fazer logout para limpar estado
        userManager.logOut("user1");

        // Testar autenticação com credenciais erradas
        assertEquals(0, userManager.authenticate("user1", "wrongpassword"));

        // Reautenticar utilizador
        assertEquals(1, userManager.authenticate("user1", "password"));

        // Testar autenticação de utilizador já autenticado
        assertEquals(2, userManager.authenticate("user1", "password"));
    }


    @Test
    void testLogOut() throws InterruptedException {
        UserManager userManager = new UserManager(10);

        userManager.register("user1", "password");
        userManager.authenticate("user1", "password");

        // Testa logout
        userManager.logOut("user1");
        assertEquals(0, userManager.getActiveSessions());
    }
}
