package gay.lemmaeof.kdlydata;

import com.google.gson.JsonElement;
import dev.hbeck.kdl.objects.KDLDocument;
import dev.hbeck.kdl.parse.KDLParser;
import gay.lemmaeof.kdlydata.mixin.MixinNamespaceResourceManager.ResourceEntryAccessor;
import gay.lemmaeof.kdlydata.mixin.NamespaceResourceManagerAccessor;
import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceIoSupplier;
import net.minecraft.resource.ResourceMetadata;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.pack.ResourcePack;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Hooks {
	private static final KDLParser PARSER = new KDLParser();
	public static final ThreadLocal<Boolean> predicateIsJson = new ThreadLocal<>();
	public static final ThreadLocal<NamespaceResourceManagerAccessor> thisManager = new ThreadLocal<>();

	public static ResourceIoSupplier<InputStream> getKdlyInputStreamSupplier(NamespaceResourceManagerAccessor manager, Identifier path, ResourcePack pack) throws IOException, IllegalArgumentException {
		InputStream input = pack.open(manager.getType(), path).get();
		KDLDocument doc = PARSER.parse(input);
		JsonElement elem = KdlProcessor.parseKdl(doc);
		return () -> new ByteArrayInputStream(elem.toString().getBytes(StandardCharsets.UTF_8));
	}
	
	public static Resource entryAsKdlyResource(NamespaceResourceManagerAccessor manager, NamespaceResourceManager.ResourceEntries entries, NamespaceResourceManager.ResourceEntry entry) {
		Identifier resourcePath = entries.path();
		boolean hasMetadata = entries.metadataSources().containsKey(entry.source());
		
		try {
			ResourceIoSupplier<InputStream> resourceSupplier = getKdlyInputStreamSupplier(manager, resourcePath, entry.source());
			
			if (hasMetadata) {
				ResourceIoSupplier<InputStream> rawMetaSupplier = entries.metadataSources().get(entry.source());
				ResourceIoSupplier<ResourceMetadata> metaSupplier = ()->ResourceMetadata.fromInputStream(rawMetaSupplier.get());
				
				return new Resource(entry.source(), resourceSupplier, metaSupplier);
			} else {
				return new Resource(entry.source(), resourceSupplier);
			}
			
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static NamespaceResourceManager.ResourceEntry entryAsKdlyEntry(NamespaceResourceManagerAccessor manager, NamespaceResourceManager.ResourceEntries entries, NamespaceResourceManager.ResourceEntry entry) {
		try {
			ResourceIoSupplier<InputStream> resourceSupplier = getKdlyInputStreamSupplier(manager, entries.path(), entry.source());
			
			return ResourceEntryAccessor.invokeInit(entry.source(), resourceSupplier);
			
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static Resource entryAsResource(NamespaceResourceManagerAccessor manager, NamespaceResourceManager.ResourceEntries entries, NamespaceResourceManager.ResourceEntry entry) {
		ResourceIoSupplier<InputStream> resourceSupplier = entry.resource();
		
		boolean hasMetadata = entries.metadataSources().containsKey(entry.source());
		if (hasMetadata) {
			ResourceIoSupplier<InputStream> rawMetaSupplier = entries.metadataSources().get(entry.source());
			ResourceIoSupplier<ResourceMetadata> metaSupplier = ()->ResourceMetadata.fromInputStream(rawMetaSupplier.get());
			return new Resource(entry.source(), resourceSupplier, metaSupplier);
		} else {
			return new Resource(entry.source(), resourceSupplier);
		}
	}
	
	public static boolean hasResource(ResourcePack pack, ResourceType type, Identifier id) {
		record Result(Identifier id, ResourceIoSupplier<InputStream> supplier) {};
		
		ArrayList<Result> results = new ArrayList<>();
		pack.listResources(type, id.getNamespace(), id.getPath(), (resId, ioSupplier)->{
			results.add(new Result(resId, ioSupplier));
		});
		
		return results.size()>0;
	}
}
