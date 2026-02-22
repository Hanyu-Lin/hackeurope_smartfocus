import torch
from datasets import load_dataset
from torch.utils.data import DataLoader, Dataset

APP_TYPES = ["sms", "email", "linkedin", "discord", "snapchat", "slack"]
CATEGORIES = ["msg", "email"]


class LangCache(Dataset):
    def __init__(
        self,
        app_types=APP_TYPES,
        categories=CATEGORIES,
        mod=10**16 + 61,
        multiplier=370984,
    ):
        super(LangCache).__init__()
        self.app_types = app_types
        self.categories = categories
        self.ds = load_dataset("redis/langcache-sentencepairs-v1", "all")["train"]
        self.size = len(self.ds)
        self.mod = mod
        self.multiplier = multiplier

    def __len__(self):
        return 2 * self.size

    def __getitem__(self, index):
        item = self.ds[index % self.size]

        same = 2 * index < len(self)

        notification_1 = {
            "appinfo": self.app_types[index % len(self.app_types)],
            "title": "",
            "text": item["sentence1"],
            "bigText": "",
            "category": self.categories[index % len(self.categories)],
        }

        if not same:
            index = (self.multiplier * index) % self.size
            item = self.ds[index]

        notification_2 = {
            "appinfo": self.app_types[index % len(self.app_types)],
            "title": "",
            "text": item["sentence2"],
            "bigText": "",
            "category": self.categories[index % len(self.categories)],
        }

        y = float(item["label"]) if same else 0
        return (notification_1, notification_2), y


class LockIn(Dataset):
    def __init__(self, datasets, preprocess=None):
        super().__init__()
        self.datasets = datasets
        self.len = sum(len(ds) for ds in datasets)
        self.ds_at_idx = torch.repeat_interleave(torch.tensor([len(ds) for ds in datasets]))
        self.idx_at_idx = torch.concat([torch.arange(len(ds)) for ds in datasets], dim=0)
        self.preprocess = preprocess

    def __len__(self):
        return self.len

    def __getitem__(self, index):
        data_point, y = self.datasets[self.ds_at_idx[index].item()][self.idx_at_idx[index].item()]
        if self.preprocess is not None:
            data_point = self.preprocess(data_point)
        return data_point, y


class NotifyAI(Dataset):
    def __init__(self):
        super(NotifyAI).__init__()
        self.ds = load_dataset("charlesfeng1/notifai-dataset")['train']

    def __len__(self):
        return self.ds.num_rows

    def __getitem__(self, index):
        item = self.ds[index]

        ret = {
            "appinfo": item["notification"]["app"],
            "title": item["notification"]["title"],
            "text": item["notification"]["body"],
            "bigText": "",
            "categpry": "email/msg"
        }
        return ret, float(item["classification"]["priority"])/5

class CustomerSupportTicketsPriority(Dataset):
    def __init__(self, *, app_types=APP_TYPES, categories=CATEGORIES, multiplyer=31):
        super(CustomerSupportTicketsPriority).__init__()
        ds = load_dataset("Tobi-Bueck/customer-support-tickets")['train']
        self.ds = ds.filter(lambda x: x["language"] == "en")
        self.app_types = app_types
        self.num_app_types = len(app_types)
        self.categories = categories
        self.num_categories = len(categories)
        self.multiplyer = multiplyer
        assert self.num_app_types != 0
        assert self.num_categories != 0
        assert multiplyer != self.num_categories and multiplyer != self.num_app_types

    def __len__(self):
        return self.ds.num_rows

    def __getitem__(self, index):
        item = self.ds[index]

        ret = {
            "appinfo": self.app_types[index % self.num_app_types],
            "title": item["subject"],
            "text": item["body"],
            "bigText": item["answer"],
            "categpry": self.categories[(self.multiplyer * index) % self.num_categories]
        }

        priority_map = {
            "low": 0.1,
            "medium": 0.5,
            "high": 1.0
        }

        y = priority_map.get(item["priority"], 0.0)

        return ret, y

class CustomerSupportTicketsSimilarity(Dataset):
    def __init__(self, *, app_types=APP_TYPES, categories=CATEGORIES, multiplyer=31, mod=10**16+61):
        super(CustomerSupportTicketsSimilarity).__init__()
        ds = load_dataset("Tobi-Bueck/customer-support-tickets")['train']
        self.ds = ds.filter(lambda x: x["language"] == "en")
        self.app_types = app_types
        self.num_app_types = len(app_types)
        self.categories = categories
        self.num_categories = len(categories)
        self.multiplyer = multiplyer
        self.mod = mod
        assert self.mod > self.ds.num_rows
        assert self.num_app_types != 0
        assert self.num_categories != 0
        assert multiplyer != self.num_categories and multiplyer != self.num_app_types

    def __len__(self):
        return self.ds.num_rows * 2

    def __getitem__(self, index):
        item = self.ds[index % self.ds.num_rows]

        same = index % 2 == 0

        notification1 = {
            "appinfo": self.app_types[index % self.num_app_types],
            "title": item["subject"],
            "text": item["body"],
            "bigText": item["answer"],
            "categpry": self.categories[(self.multiplyer * index) % self.num_categories]
        }

        if not same:
            index = (index * self.mod) % self.ds.num_rows

        notification2 = {
            "appinfo": self.app_types[(self.multiplyer * index) % self.num_app_types],
            "title": item["subject"],
            "text": item["body"],
            "bigText": item["answer"],
            "categpry": self.categories[self.multiplyer % self.num_categories]
        }

        y = 1.0 if same else 0.0

        return (notification1, notification2), y
