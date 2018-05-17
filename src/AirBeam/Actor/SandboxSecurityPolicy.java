package AirBeam.Actor;
import java.security.*;

public class SandboxSecurityPolicy extends Policy {

    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        if (isPlugin(domain)) {
            return pluginPermissions();
        }
        else {
            return super.getPermissions(domain);
        }
    }

    private boolean isPlugin(ProtectionDomain domain) {
        return domain.getClassLoader() instanceof AirQualityActor.CompiledClassLoader;
    }

    private PermissionCollection pluginPermissions() {
        Permissions permissions = new Permissions(); // No permissions
        return permissions;
    }
}