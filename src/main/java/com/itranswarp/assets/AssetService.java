package com.itranswarp.assets;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AssetService {

    // userId -> (assetId -> Asset)
    public ConcurrentMap<Long, ConcurrentMap<String, Asset>> userAssets = new ConcurrentHashMap<>();

    public Asset getAsset(Long userId, String assetId) {
        Map<String, Asset> assets = userAssets.get(userId);
        if (assets == null) {
            return null;
        }
        return assets.get(assetId);
    }

    public boolean tryFreeze(Long userId, String assetId, BigDecimal amount) {
        return tryTransfer(Transfer.AVAILABLE_TO_FROZEN, userId, userId, assetId, amount, true);
    }

    public void unfreeze(Long userId, String assetId, BigDecimal amount) {
        if (!tryTransfer(Transfer.FROZEN_TO_AVAILABLE, userId, userId, assetId, amount, true)) {
            throw new RuntimeException("Unfreeze failed for user " + userId + ", asset = " + assetId + ", amount = " + amount);
        }
    }

    public void transfer(Transfer type, Long fromUser, Long toUser, String assetId, BigDecimal amount) {
        if (!tryTransfer(type, fromUser, toUser, assetId, amount, true)) {
            throw new RuntimeException(
                    "Transfer failed for " + type + ", from user " + fromUser + " to user " + toUser + ", asset = " + assetId + ", amount = " + amount);
        }
    }

    public boolean tryTransfer(Transfer type, Long fromUser, Long toUser, String assetId, BigDecimal amount, boolean checkBalance) {
        if (amount.signum() == 0) {
            return true;
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Negative amount");
        }
        Asset fromAsset = getAsset(fromUser, assetId);
        if (fromAsset == null) {
            fromAsset = initAssets(fromUser, assetId);
        }
        Asset toAsset = getAsset(toUser, assetId);
        if (toAsset == null) {
            toAsset = initAssets(toUser, assetId);
        }
        return switch (type) {
        case AVAILABLE_TO_AVAILABLE -> {
            // 需要检查余额且余额不足:
            if (checkBalance && fromAsset.available.compareTo(amount) < 0) {
                yield false;
            }
            fromAsset.available = fromAsset.available.subtract(amount);
            toAsset.available = toAsset.available.add(amount);
            yield true;
        }
        case AVAILABLE_TO_FROZEN -> {
            // 需要检查余额且余额不足:
            if (checkBalance && fromAsset.available.compareTo(amount) < 0) {
                yield false;
            }
            fromAsset.available = fromAsset.available.subtract(amount);
            toAsset.frozen = toAsset.frozen.add(amount);
            yield true;
        }
        case FROZEN_TO_AVAILABLE -> {
            // 需要检查余额且余额不足:
            if (checkBalance && fromAsset.frozen.compareTo(amount) < 0) {
                yield false;
            }
            fromAsset.frozen = fromAsset.frozen.subtract(amount);
            toAsset.available = toAsset.available.add(amount);
            yield true;
        }
        default -> {
            throw new IllegalArgumentException("invalid type: " + type);
        }
        };
    }

    Asset initAssets(Long userId, String assetId) {
        ConcurrentMap<String, Asset> map = userAssets.get(userId);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            userAssets.put(userId, map);
        }
        Asset zeroAsset = new Asset();
        map.put(assetId, zeroAsset);
        return zeroAsset;
    }

    public void debug() {
        System.out.println("---------- assets ----------");
        List<Long> userIds = new ArrayList<>(userAssets.keySet());
        Collections.sort(userIds);
        for (Long userId : userIds) {
            System.out.println("  user " + userId + " ----------");
            Map<String, Asset> assets = userAssets.get(userId);
            List<String> assetIds = new ArrayList<>(assets.keySet());
            Collections.sort(assetIds);
            for (String assetId : assetIds) {
                System.out.println("    " + assetId + ": " + assets.get(assetId));
            }
        }
        System.out.println("---------- // assets ----------");
    }
}
