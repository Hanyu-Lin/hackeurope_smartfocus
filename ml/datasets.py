import torch
from torch.utils.data import Dataset, DataLoader
from datasets import load_dataset

class LockIn(Dataset):
    def __init__(self, datasets, preprocess=None):
        super().__init__()
        self.datasets = datasets
        self.len = sum(len(ds) for ds in datasets)
        self.ds_at_idx = torch.repeat_interleave(torch.tensor([len(ds) for ds in datasets]))
        self.idx_at_idx = torch.arange(torch.tensor([len(ds) for ds in datasets]))
        self.preprocess = preprocess
        
    def __len__(self):
        return self.len
    
    def __getitem__(self, index):
        data_point, y = self.datasets[self.ds_at_idx[index]][self.idx_at_idx[index]]
        if self.preprocess is not None:
            data_point = self.preprocess(data_point)
        return data_point, y
    
class NotifyAI(Dataset):
    def __init__(self):
        super(NotifyAI).__init__()
        self.ds = load_dataset("charlesfeng1/notifai-dataset")['train']
        
    def __len__(self):
        return ds['num_rows']
    
    def __getitem__(self, index):
        item = self.ds[index]
        
        ret = {
            "appinfo": item["app"],
            "title": item["title"],
            "text": item["body"],
            "bigText": "",
            "categpry": "email/msg"
        }
        return ret, item["priority"]