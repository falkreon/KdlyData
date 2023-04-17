package gay.lemmaeof.kdlydata.mixin;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.ResourceIoSupplier;
import net.minecraft.resource.pack.ResourcePack;
import net.minecraft.util.Identifier;

@Mixin(NamespaceResourceManager.ResourceEntries.class)
public interface ResourceEntriesAccessor {
	@Invoker("<init>")
	public static NamespaceResourceManager.ResourceEntries invokeInit(
			Identifier path,
			Identifier metadataId,
			List<NamespaceResourceManager.ResourceEntry> fileSources,
			Map<ResourcePack, ResourceIoSupplier<InputStream>> metadataSources
			) {
		throw new AssertionError("impossible");
	}
}