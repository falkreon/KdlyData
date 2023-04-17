package gay.lemmaeof.kdlydata;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import gay.lemmaeof.kdlydata.mixin.NamespaceResourceManagerAccessor;
import gay.lemmaeof.kdlydata.mixin.ResourceEntriesAccessor;
import gay.lemmaeof.kdlydata.mixin.MixinNamespaceResourceManager.ResourceEntryAccessor;
import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceIoSupplier;
import net.minecraft.resource.ResourceMetadata;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.pack.ResourcePack;
import net.minecraft.util.Identifier;

/**
 * Represents a *specific* resource with enough detail and context that we can manipulate the game's internal resource
 * handling mechanisms.
 */
public class ResourceInfo {
	private final NamespaceResourceManager.ResourceEntries entries;
	private final NamespaceResourceManager.ResourceEntry entry;
	
	/**
	 * Constructs a ResourceInfo from an existing, related pair of ResourceEntries+ResourceEntry. If entries does not
	 * contain entry, undefined behavior occurs.
	 */
	public ResourceInfo(NamespaceResourceManager.ResourceEntries entries, NamespaceResourceManager.ResourceEntry entry) {
		this.entries = entries;
		this.entry = entry;
	}
	
	/**
	 * Constructs a ResourceInfo from sufficient knowledge about the resource. This creates new objects unknown to any
	 * ResourcePack.
	 */
	public ResourceInfo(ResourcePack resourcePack, Identifier resourceId, ResourceIoSupplier<InputStream> resourceSupplier, Identifier metaId) {
		this.entries = ResourceEntriesAccessor.invokeInit(resourceId, metaId, new ArrayList<>(), new HashMap<>());
		this.entry = ResourceEntryAccessor.invokeInit(resourcePack, resourceSupplier);
		this.entries.fileSources().add(entry);
	}
	
	/**
	 * Constructs a ResourceInfo from sufficient knowledge about the resource and its metadata. This creates new objects
	 * unknown to any ResourcePack.
	 */
	public ResourceInfo(ResourcePack resourcePack, Identifier resourceId, ResourceIoSupplier<InputStream> resourceSupplier, ResourcePack metaPack, Identifier metaId, ResourceIoSupplier<InputStream> metaSupplier) {
		this.entries = ResourceEntriesAccessor.invokeInit(resourceId, metaId, new ArrayList<>(), new HashMap<>());
		this.entry = ResourceEntryAccessor.invokeInit(resourcePack, resourceSupplier);
		this.entries.metadataSources().put(metaPack, metaSupplier);
		this.entries.fileSources().add(entry);
	}
	
	/**
	 * Uses knowledge contained in this object to construct a Resource supplying unchanged data.
	 */
	public Resource asBareResource() {
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
	
	/**
	 * Assuming this object points to a json Resource, and there is a corresponding .kdl resource, creates a supplier
	 * that supplies synthetic json converted from the kdl resource.
	 */
	public ResourceIoSupplier<InputStream> asKdlyResourceSupplier(NamespaceResourceManagerAccessor resourceManager) {
		try {
			return Hooks.getKdlyInputStreamSupplier(resourceManager, getKdlyPath(entries.path()), entry.source());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Assuming this object points to a json Resource, and there is a corresponding .kdl resource, creates a Resource that
	 * supplies synthetic json converted from the kdl resource.
	 */
	public Resource asKdlyResource(NamespaceResourceManagerAccessor resourceManager) {
		try {
			ResourceIoSupplier<InputStream> kdlyResourceSupplier = Hooks.getKdlyInputStreamSupplier(resourceManager, getKdlyPath(entries.path()), entry.source());
			
			boolean hasMetadata = entries.metadataSources().containsKey(entry.source());
			if (hasMetadata) {
				ResourceIoSupplier<InputStream> rawMetaSupplier = entries.metadataSources().get(entry.source());
				ResourceIoSupplier<ResourceMetadata> metaSupplier = ()->ResourceMetadata.fromInputStream(rawMetaSupplier.get());
				return new Resource(entry.source(), kdlyResourceSupplier, metaSupplier);
			} else {
				return new Resource(entry.source(), kdlyResourceSupplier);
			}
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * If this object points to a [json] resource path with a corresponding kdl resource, returns a supplier supplying
	 * synthetic json for it. Otherwise, returns empty.
	 */
	public Optional<ResourceIoSupplier<InputStream>> tryKdlyResourceSupplier(NamespaceResourceManagerAccessor resourceManager, ResourceType type) {
		if (!Hooks.hasResource(entry.source(), type, getKdlyPath(entries.path()))) return Optional.empty();
		return Optional.of(asKdlyResourceSupplier(resourceManager));
	}
	
	/**
	 * If this object points to a [json] resource path with a corresponding kdl resource, returns a Resource supplying
	 * synthetic json for it. Otherwise, returns empty.
	 */
	public Optional<Resource> tryKdlyResource(NamespaceResourceManagerAccessor resourceManager, ResourceType type) {
		if (!Hooks.hasResource(entry.source(), type, getKdlyPath(entries.path()))) return Optional.empty();
		return Optional.of(asKdlyResource(resourceManager));
	}
	
	public NamespaceResourceManager.ResourceEntries getEntries() {
		return entries;
	}
	
	public NamespaceResourceManager.ResourceEntry getEntry() {
		return entry;
	}
	
	/**
	 * Given an Identifier pointing to json, produces an Identifier that would point to the corresponding kdl if it exists
	 */
	public static Identifier getKdlyPath(Identifier jsonId) {
		String basePath = jsonId.getPath();
		if (basePath.endsWith(".json")) {
			basePath = basePath.substring(0, basePath.length()-5);
		} else if (basePath.endsWith(".kdl")) {
			return jsonId;
		} //else if it's something like `foo.txt` we'll return `foo.txt.kdl`
		return new Identifier(jsonId.getNamespace(), basePath+".kdl");
	}
}